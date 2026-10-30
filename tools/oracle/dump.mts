/**
 * Oracle: runs Capella's TypeScript generator on a fixture scene the way the Java port sees it
 * (crypt off, fields reduced to terrainHeight, no overrides, detail 'shell') and writes
 *   <out>.scene.json     the reduced scene
 *   <out>.elements.json  every element (id, kind, role, level, bounds, geometry, params...)
 *   <out>.blocks.txt     "x y z state" for every non-air voxel of the final volume
 *
 * Run from a checkout of tjhgibbs-git/mc-gothic-generator with its dependencies installed:
 *   npx tsx ../capella-fabric/tools/oracle/dump.mts fixtures/scenes/parish.json \
 *       ../capella-fabric/core/src/test/resources/oracle/parish
 * Set CAPELLA_TS to the checkout if it is not the current directory.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(process.env.CAPELLA_TS ?? '.');
const load = (p: string) => import(pathToFileURL(resolve(root, p)).href);
const { sceneFromJson } = await load('src/core/document.ts');
const { generate } = await load('src/core/generate.ts');

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
const v = build.volume;
const b = v.bounds;
const blocks: string[] = [];
for (let y = b.min.y; y < b.max.y; y++)
  for (let z = b.min.z; z < b.max.z; z++)
    for (let x = b.min.x; x < b.max.x; x++) {
      const k = v.keyAt(x, y, z);
      if (k !== 'minecraft:air') blocks.push(`${x} ${y} ${z} ${k}`);
    }
writeFileSync(out + '.blocks.txt', blocks.join('\n') + '\n');
writeFileSync(out + '.elements.json', JSON.stringify(build.elements.toJSON()));
writeFileSync(out + '.scene.json', JSON.stringify(raw, null, 2));
console.log(input, 'bounds', JSON.stringify(b), 'blocks', blocks.length, 'elements', build.elements.size);
