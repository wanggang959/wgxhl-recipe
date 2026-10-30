# Extra Common Cooking Catalog

Published 2026-10-30 (Asia/Hong_Kong), following the user's approval to add
remaining frequently used ingredients and seasonings.

- Added 24 public ingredients and 8 public seasonings.
- Production totals after import: 123 public ingredients and 68 public seasonings.
- All entries have a short culinary description and a 512 x 512 WebP image.
- The 32 final images total 978,214 bytes.

## Files And Generation

Final images are retained under `assets/`, with relative COS object keys in
`manifest.json`. `review-1.jpg` and `review-2.jpg` are labeled contact sheets
inspected before publication. `import.sql` only inserts missing public names;
existing public records and personal records are not changed.

Source names, descriptions, and the individual image subjects are in
`scripts/catalog/common-catalog-extra-20261030.json` at the repository root.
Each image was generated separately with the built-in image generation tool.
The shared prompt was the original batch's product-mockup prompt: an accurate,
recognizable grocery ingredient on a pure white background, centered with safe
margins, soft studio lighting, realistic color and texture, and no text,
branding, unrelated garnish, or kitchen scenery. The English subject and
Chinese name from each source entry were inserted into that prompt. The full
shared prompt is recorded in `deploy/catalog/common-20261030/README.md`.

Rebuild locally with `node scripts/catalog/build-common-catalog.mjs --extra`.
This requires the local-only generated PNG mapping in
`tmp/catalog-sources-extra-20261030.json`. Deployment can use the retained final
images without rebuilding or generating them again.

## Verification And Publication

- All 32 final images were checked for 512 x 512 WebP format.
- `scripts/catalog/check-extra-import.sh` imported the catalog twice into its
  own isolated MySQL database. Counts stayed unchanged on rerun; existing,
  unrelated public, and personal records were preserved.
- Every uploaded object was fetched anonymously from COS and checked for
  matching SHA-256 content and `image/webp` content type before import.
- After import, production pagination APIs verified every new name,
  description, and relative image path. All 73 images/descriptions from the
  previous batch and the original 86 descriptions were also checked unchanged.
- No public entry has an empty description or image after this release.

Production backup:
`/opt/wgxhl-deploy/backups/catalog-extra-20261030-142708/recipe.sql`.
Server staging directory:
`/opt/wgxhl-deploy/staging/catalog-common-extra-20261030/`.

This was a data-only publication; no application deployment or restart was
performed. Pending app-icon changes were not released by this operation.
