# Link Shortening

A service that issues short, opaque codes standing in for longer destinations, and resolves those codes back into redirects. A single bounded context.

## Language

**ShortLink**:
The aggregate: a mapping from a ShortCode to a TargetUrl, together with its creator, its lifetime, and its state.
_Avoid_: url, mapping, entry, record

**ShortCode**:
The opaque key that appears in the path of a shortened URL, e.g. the `aB3xK9` in `https://sho.rt/aB3xK9`.
_Avoid_: slug, key, hash, id, token

**Alias**:
A ShortCode chosen by the creator rather than generated. Occupies the same namespace as a generated ShortCode and is subject to the same uniqueness.
_Avoid_: custom slug, vanity url, custom code

**TargetUrl**:
The destination a ShortLink points at. Named for what it is, not for its length: the ShortLink is not a derivative of it.
_Avoid_: long url, original url, destination url, source url

**Resolution**:
One act of looking up a ShortCode and returning a redirect for it.
_Avoid_: hit, lookup, visit, request

**Click**:
A Resolution that was successfully counted. Deliberately distinct from Resolution: caching and asynchronous counting mean the two quantities are not equal, and the gap between them is a stated guarantee rather than a defect.
_Avoid_: view, visit, hit, impression

**Deactivation**:
Retiring a ShortLink so that it stops resolving. A ShortLink is never destroyed and its ShortCode is never returned to the pool, because a reissued ShortCode would silently point an old audience at a new destination.
_Avoid_: deletion, removal, disabling
