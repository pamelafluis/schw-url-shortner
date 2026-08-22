package com.schw.urlshortener.link.persistence;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

@Repository
public class JdbcShortLinkRepository implements ShortLinkRepository {

	private static final int MAX_GENERATION_ATTEMPTS = 3;

	private final JdbcAggregateTemplate template;
	private final Supplier<ShortCode> codeGenerator;

	@Autowired
	public JdbcShortLinkRepository(JdbcAggregateTemplate template) {
		this(template, ShortCode::generate);
	}

	// Test-only seam: lets the collision-retry path be exercised deterministically
	// instead of relying on an astronomically unlikely real random collision.
	JdbcShortLinkRepository(JdbcAggregateTemplate template, Supplier<ShortCode> codeGenerator) {
		this.template = template;
		this.codeGenerator = codeGenerator;
	}

	@Override
	public ShortLink saveGenerated(TargetUrl targetUrl, String createdBy, Instant createdAt,
			Optional<Instant> expiresAt) {
		DuplicateKeyException lastConflict = null;
		for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
			ShortLink candidate = new ShortLink(codeGenerator.get(), targetUrl, createdBy, createdAt, expiresAt);
			try {
				template.insert(toEntity(candidate));
				return candidate;
			}
			catch (DuplicateKeyException e) {
				lastConflict = e;
			}
		}
		throw new ShortCodeGenerationExhaustedException(MAX_GENERATION_ATTEMPTS, lastConflict);
	}

	@Override
	public ShortLink saveWithAlias(ShortCode alias, TargetUrl targetUrl, String createdBy, Instant createdAt,
			Optional<Instant> expiresAt) {
		ShortLink shortLink = new ShortLink(alias, targetUrl, createdBy, createdAt, expiresAt);
		try {
			template.insert(toEntity(shortLink));
			return shortLink;
		}
		catch (DuplicateKeyException e) {
			throw new AliasConflictException(alias.value());
		}
	}

	@Override
	public Optional<ShortLink> findByCode(ShortCode code) {
		return Optional.ofNullable(template.findById(code.value(), ShortLinkEntity.class)).map(this::toDomain);
	}

	@Override
	public void deactivate(ShortCode code) {
		ShortLinkEntity entity = template.findById(code.value(), ShortLinkEntity.class);
		if (entity == null) {
			throw new NoSuchElementException("ShortCode '%s' was never issued".formatted(code.value()));
		}
		template.update(new ShortLinkEntity(entity.shortCode(), entity.targetUrl(), entity.createdBy(),
				entity.createdAt(), entity.expiresAt(), false));
	}

	private static ShortLinkEntity toEntity(ShortLink shortLink) {
		return new ShortLinkEntity(shortLink.code().value(), shortLink.targetUrl().value(), shortLink.createdBy(),
				shortLink.createdAt(), shortLink.expiresAt().orElse(null), shortLink.active());
	}

	private ShortLink toDomain(ShortLinkEntity entity) {
		ShortLink shortLink = new ShortLink(ShortCode.reconstitute(entity.shortCode()),
				TargetUrl.reconstitute(entity.targetUrl()), entity.createdBy(), entity.createdAt(),
				Optional.ofNullable(entity.expiresAt()));
		if (!entity.active()) {
			shortLink.deactivate();
		}
		return shortLink;
	}

}
