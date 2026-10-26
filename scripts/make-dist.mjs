// Copies the MillionOS game and what it needs into dist/, the folder the Windows and Mac apps are built from.
// The web version (GitHub Pages) keeps serving these same files from the repository root.
import { rm, mkdir, cp } from 'node:fs/promises';

const files = ['index.html', 'sw.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png', 'icon.ico', 'og.png', 'vendor'];
await rm('dist', { recursive: true, force: true });
await mkdir('dist');
for (const f of files) await cp(f, `dist/${f}`, { recursive: true });
console.log(`dist/: ${files.join(', ')}`);
