/**
 * Oracle: runs Capella's TypeScript generator on a fixture scene the way the Java port sees it
 * (crypt off, fields reduced to terrainHeight, no overrides, detail 'shell'), drops the footing's
 * elements (the port floats: no plinth, precinct or approach steps), rasterizes and normalizes what
 * is left exactly as the port does, and writes
 *   <out>.scene.json        the reduced scene
 *   <out>.elements.json.gz  every surviving element (id, kind, role, level, bounds, geometry...)
 *   <out>.blocks.txt        "x y z state" for every non-air voxel of that volume (for compare.py)
 *   <out>.oracle.txt        block count and SHA-256 of blocks.txt, which OracleTest checks
 * Everything but blocks.txt is small enough to commit under core/src/test/resources/oracle/.
 *
 * Run from a checkout of tjhgibbs-git/mc-gothic-generator with its dependencies installed:
 *   npx tsx ../capella-fabric/tools/oracle/dump.mts fixtures/scenes/parish.json \
 *       ../capella-fabric/core/src/test/resources/oracle/parish
 * Set CAPELLA_TS to the checkout if it is not the current directory.
 */
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(process.env.CAPELLA_TS ?? '.');
const load = (p: string) => import(pathToFileURL(resolve(root, p)).href);
const { sceneFromJson } = await load('src/core/document.ts');
const { generate } = await load('src/core/generate.ts');
const { ElementTree } = await load('src/core/elements.ts');
const { rasterize } = await load('src/core/raster/rasterize.ts');
const { normalizeVolume } = await load('src/core/raster/normalize.ts');
const { getPalette } = await load('src/core/palette.ts');

/** What `footing.ts` and the earthwork emit; the port builds none of it. */
const FOOTING =
  /^(plinth\/|terrain\/|floor\/precinct$|wall\/revetment$|aperture\/excavation$|stair\/approach-|stair\/descent-|aperture\/descent-)/;

const [, , input, out] = process.argv;
if (!input || !out) throw new Error('usage: dump.mts <scene.json> <out-prefix>');
const raw = JSON.parse(readFileSync(input, 'utf8'));
const strip = (p: Record<string, unknown> | undefined) => {
  if (p) p['crypt'] = false;
};
strip(raw.plan);
for (const s of raw.structures ?? []) strip(s.params);
const th = raw.fields?.terrainHeight;
raw.fields = th ? { terrainHeight: th } : {};
raw.overrides = [];
const scene = sceneFromJson(JSON.stringify(raw), input);
const build = generate(scene, { detail: 'shell' });
const tree = new ElementTree(build.elements.elements.filter((e: { id: string }) => !FOOTING.test(e.id)));
const { volume: v } = rasterize(scene, tree, { palette: getPalette(scene.palette) });
normalizeVolume(v);
const b = v.bounds;
const blocks: string[] = [];
for (let y = b.min.y; y < b.max.y; y++)
  for (let z = b.min.z; z < b.max.z; z++)
    for (let x = b.min.x; x < b.max.x; x++) {
      const k = v.keyAt(x, y, z);
      if (k !== 'minecraft:air') blocks.push(`${x} ${y} ${z} ${k}`);
    }
const text = blocks.join('\n') + '\n';
writeFileSync(out + '.blocks.txt', text);
writeFileSync(out + '.oracle.txt', `blocks ${blocks.length}\nsha256 ${createHash('sha256').update(text).digest('hex')}\n`);
writeFileSync(out + '.elements.json.gz', gzipSync(JSON.stringify(tree.toJSON())));
writeFileSync(out + '.scene.json', JSON.stringify(raw, null, 2));
console.log(input, 'bounds', JSON.stringify(b), 'blocks', blocks.length, 'elements', tree.size);
