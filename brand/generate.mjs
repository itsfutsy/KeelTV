import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';
import opentype from 'opentype.js';

const root = dirname(fileURLToPath(import.meta.url));
const read = p => readFileSync(resolve(root, p), 'utf8');
function write(p, data) {
  const dest = resolve(root, p);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, data);
}
const app = resolve(root, '..');
const mark = read('mark.svg');
const shapes = mark.match(/<path\b[^>]+\/>/g).join('\n');
function loadFont(name) {
  const bytes = readFileSync(resolve(root, name));
  return opentype.parse(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
}
const wordmarkFont = loadFont('manrope-semibold.ttf');
const svg = (w, h, body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>\n`;
const group = (x, y, scale, body = shapes) => `<g transform="translate(${x} ${y}) scale(${scale})">${body}</g>`;
function lettering(value, x, y, size, face, tracking = 0, pairSpacing = {}) {
  const glyphs = [...value].map(c => face.charToGlyph(c));
  const scale = size / face.unitsPerEm;
  let cursor = x;
  let path = '';
  glyphs.forEach((glyph, i) => {
    const outline = glyph.getPath(cursor, y, size).toPathData(3);
    path += outline + (outline ? 'Z' : '');
    cursor += (glyph.advanceWidth + (glyphs[i + 1] ? face.getKerningValue(glyph, glyphs[i + 1]) : 0)) * scale;
    if (glyphs[i + 1]) cursor += tracking + (pairSpacing[value.slice(i, i + 2)] ?? 0);
  });
  return { path, width: cursor - x };
}
const lockupHeight = 256;
const wordmarkSize = 156 * wordmarkFont.unitsPerEm / wordmarkFont.charToGlyph('K').getBoundingBox().y2;
const wordmarkX = 286 - wordmarkFont.charToGlyph('K').getBoundingBox().x1 * wordmarkSize / wordmarkFont.unitsPerEm;
const wordmarkOutline = lettering('Keel TV', wordmarkX, 195, wordmarkSize, wordmarkFont, -2.6, { TV: 10.4 });
const wordmark = fill => `<path fill="${fill}" d="${wordmarkOutline.path}"/>`;
const lockupWidth = Math.ceil(wordmarkX + wordmarkOutline.width);
const lockup = fill => shapes + wordmark(fill);
const render = (source, width) => new Resvg(source, { fitTo: { mode: 'width', value: width } }).render().asPng();
const png = (p, source, width) => write(p, render(source, width));

for (const [theme, fill] of [['light', '#0F0F11'], ['dark', '#F2F2F4']]) {
  const logo = svg(lockupWidth, lockupHeight, lockup(fill));
  write(`logo-${theme}.svg`, logo);
  png(resolve(app, `docs/${theme === 'dark' ? 'logo-dark' : 'logo'}.png`), logo, 1200);
}
const icon = svg(1024, 1024, '<rect x="32" y="32" width="960" height="960" rx="216" fill="#0F0F11"/>' + group(168, 168, 688 / 256));
write('app-icon.svg', icon);

const res = resolve(app, 'app/src/main/res');
const roundIcon = svg(1024, 1024, '<circle cx="512" cy="512" r="480" fill="#0F0F11"/>' + group(168, 168, 688 / 256));
for (const [density, size] of [['mdpi', 80], ['hdpi', 120], ['xhdpi', 160], ['xxhdpi', 240], ['xxxhdpi', 320]]) {
  png(`${res}/mipmap-${density}/ic_launcher.png`, icon, size);
  png(`${res}/mipmap-${density}/ic_launcher_round.png`, roundIcon, size);
}
const bannerScale = 252 / lockupWidth;
const banner = svg(320, 180, '<path fill="#0F0F11" d="M0 0H320V180H0Z"/>' + group(34, (180 - lockupHeight * bannerScale) / 2, bannerScale, lockup('#F2F2F4')));
for (const [density, width] of [['mdpi', 160], ['hdpi', 240], ['xhdpi', 320], ['xxhdpi', 480], ['xxxhdpi', 640]]) {
  png(`${res}/drawable-${density}/banner.png`, banner, width);
}
const androidArtwork = body => body
  .replaceAll('fill-rule="evenodd"', 'android:fillType="evenOdd"')
  .replaceAll(/\bfill="([^"]+)"/g, 'android:fillColor="$1"')
  .replaceAll(/\bd="([^"]+)"/g, 'android:pathData="$1"');
const androidPaths = androidArtwork(shapes);
const vector = (w, h, body, displayWidth = w) => `<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="${displayWidth}dp" android:height="${(displayWidth * h / w).toFixed(3)}dp" android:viewportWidth="${w}" android:viewportHeight="${h}">${body}</vector>\n`;
write(`${res}/drawable/keel_mark.xml`, vector(256, 256, androidPaths, 64));
write(`${res}/drawable/keel_logo.xml`, vector(lockupWidth, lockupHeight, androidArtwork(lockup('#F2F2F4')), 184));
const adaptiveGroup = body => `<group android:translateX="27" android:translateY="27" android:scaleX="0.2109375" android:scaleY="0.2109375">${body}</group>`;
const foreground = adaptiveGroup(androidPaths);
write(`${res}/drawable/ic_launcher_foreground.xml`, vector(108, 108, foreground));
const monoData = shapes.split('\n').filter(path => !path.includes('fill="#FFFFFF"'))
  .map(path => path.match(/\bd="([^"]+)"/)[1]).join(' ');
const monoPaths = `<path android:fillColor="#FFFFFF" android:fillType="evenOdd" android:pathData="${monoData}"/>`;
write(`${res}/drawable/ic_launcher_monochrome.xml`, vector(108, 108, adaptiveGroup(monoPaths)));
const adaptive = mono => `<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/bg_app"/><foreground android:drawable="@drawable/ic_launcher_foreground"/>${mono ? '<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>' : ''}</adaptive-icon>\n`;
write(`${res}/mipmap-anydpi-v26/ic_launcher.xml`, adaptive(true));
write(`${res}/mipmap-anydpi-v26/ic_launcher_round.xml`, adaptive(true));
write(`${res}/mipmap-anydpi-v33/ic_launcher.xml`, adaptive(true));
write(`${res}/mipmap-anydpi-v33/ic_launcher_round.xml`, adaptive(true));

console.log('Generated Keel TV vectors and Android icons/banners.');
