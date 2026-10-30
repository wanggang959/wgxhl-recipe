# Common public cooking catalog

Published on 2026-10-30 (Asia/Hong_Kong). Includes 55 ingredients and 18
seasonings. Two ingredients (eggplant and zucchini) were published first and
visually approved by the user before continuing the batch. Production totals
after the full import: 99 public ingredients and 60 public seasonings.

## Contents

- `assets/`: 73 final 512 x 512 WebP images, 2,103,768 bytes in total.
- `manifest.json`: names, descriptions, IDs, and relative COS object keys.
- `import.sql`: transactional, repeatable import. Existing names are preserved;
  personal records are not modified. The oyster sauce spelling correction keeps
  its original ID and updates the redundant name in recipe relations.
- `review-1.jpg` through `review-5.jpg`: labeled contact sheets used for review.
- `scripts/catalog/common-catalog-20261030.json`: curated entries and individual
  image subjects; this is the source list for the batch.

## Generation

Images were generated individually with the built-in image generation tool,
not downloaded stock photos. Each subject in the source list used this prompt:

> Use case: product-mockup. ONE square photographic grocery catalog asset.
> Subject: [English subject] ([Chinese name]). Only this ingredient,
> recognizable and botanically/culinarily accurate, uncooked except where
> explicitly specified. Pure white seamless background, centered complete item
> or neat small group, occupies 70-80% of frame, safe margins on all sides.
> Soft studio daylight, natural true colors, sharp realistic textures, subtle
> contact shadow. Plain unbranded bowl for loose grains/powders/sauces only.
> No unrelated ingredients or garnishes, no kitchen setting, no text, labels,
> watermark, hands or border. Match a clean Chinese home-cooking ingredient
> photo library. Not a cooked dish, not an illustration, not a collage.

`scripts/catalog/build-common-catalog.mjs` performs the deterministic resizing,
WebP compression, contact-sheet assembly, and SQL generation. The source PNG
mapping is local-only in `tmp/catalog-sources-20261030.json`. The final WebP
files are retained here, so deployment does not require regenerating images.

## Checks And Publication

All 73 image dimensions and formats were checked locally. Every uploaded object
was downloaded anonymously from COS and checked against its local SHA-256 and
`image/webp` content type before the database import. Production ingredient and
seasoning pagination APIs were checked for every new name and relative image
path after import.

The isolated MySQL check (`scripts/catalog/check-import.sh`) imports twice and
checks duplicate prevention, existing images/descriptions, personal records,
and the spelling correction's recipe references. It uses its own
`recipe_catalog_check_20261030` database, not the app's test database tables.

Production backup before the full import:
`/opt/wgxhl-deploy/backups/catalog-full-20261030-120243/recipe.sql`.
Staged release files:
`/opt/wgxhl-deploy/staging/catalog-common-20261030/`.

Only catalog data and images were published. No application deployment or
backend restart was needed; pending app-icon code changes were not released.

## Original Catalog Descriptions

The original 44 ingredients and 42 seasonings also had empty descriptions.
`fill-existing-descriptions.sql` fills those blanks only, using
`scripts/catalog/existing-descriptions-20261030.json` as its source. It leaves
existing text, personal records, names, IDs, images, and recipe relations alone.
Only description and update timestamp fields are updated.

`scripts/catalog/check-descriptions.sh` verifies NULL/whitespace filling,
nonempty-text preservation, personal-data preservation, unchanged row counts,
and repeat execution in its own isolated MySQL database. After production
execution, all 99 public ingredients and 60 public seasonings have descriptions;
every original description was checked through the production pagination APIs.
All 73 recent additions' descriptions and image paths were also checked unchanged.

Backup before this description update:
`/opt/wgxhl-deploy/backups/catalog-descriptions-20261030-121715/recipe.sql`.
