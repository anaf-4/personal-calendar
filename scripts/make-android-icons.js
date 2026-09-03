const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

let crcTable = null;
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) {
        c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      }
      crcTable[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc = crcTable[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function buildPng(width, height, pixelFn) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  let offset = 0;
  for (let y = 0; y < height; y++) {
    raw[offset++] = 0;
    for (let x = 0; x < width; x++) {
      const [r, g, b, a] = pixelFn(x, y);
      raw[offset++] = r;
      raw[offset++] = g;
      raw[offset++] = b;
      raw[offset++] = a;
    }
  }
  const idatData = zlib.deflateSync(raw, { level: 9 });
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idatData), chunk('IEND', Buffer.alloc(0))]);
}

function insideRoundRect(px, py, l, t, r, b, rad) {
  if (px < l || px > r || py < t || py > b) return false;
  const nx = Math.min(px - l, r - px);
  const ny = Math.min(py - t, b - py);
  if (nx >= rad || ny >= rad) return true;
  const ddx = rad - nx;
  const ddy = rad - ny;
  return ddx * ddx + ddy * ddy <= rad * rad;
}

function makeDrawIcon(SIZE) {
  return function drawIcon(x, y) {
    const cx = SIZE / 2;
    const cy = SIZE / 2;
    const bgColor = [91, 141, 239, 255];
    const white = [255, 255, 255, 255];
    const red = [224, 89, 107, 255];
    const dark = [40, 60, 110, 255];
    const gridLine = [210, 220, 235, 255];

    const half = SIZE / 2 - Math.max(1, SIZE * 0.023);
    const radius = SIZE * 0.22;
    const dx = Math.abs(x - cx);
    const dy = Math.abs(y - cy);
    let inBg;
    if (dx <= half - radius || dy <= half - radius) {
      inBg = dx <= half && dy <= half;
    } else {
      const cornerDx = dx - (half - radius);
      const cornerDy = dy - (half - radius);
      inBg = cornerDx * cornerDx + cornerDy * cornerDy <= radius * radius;
    }
    if (!inBg) return [0, 0, 0, 0];

    const pad = SIZE * 0.14;
    const pageLeft = pad;
    const pageRight = SIZE - pad;
    const pageTop = SIZE * 0.24;
    const pageBottom = SIZE - pad;
    const pageRadius = SIZE * 0.04;

    if (insideRoundRect(x, y, pageLeft, pageTop, pageRight, pageBottom, pageRadius)) {
      const headerBottom = pageTop + SIZE * 0.1;
      if (y <= headerBottom) return red;
      const rows = 4;
      const rowHeight = (pageBottom - headerBottom) / (rows + 1);
      for (let r = 1; r <= rows; r++) {
        const lineY = headerBottom + rowHeight * r;
        if (Math.abs(y - lineY) < Math.max(1, SIZE * 0.008) && x > pageLeft + SIZE * 0.04 && x < pageRight - SIZE * 0.04) {
          return gridLine;
        }
      }
      return white;
    }

    const ringY = pageTop;
    const ringRadius = SIZE * 0.022;
    const ring1X = pageLeft + (pageRight - pageLeft) * 0.28;
    const ring2X = pageLeft + (pageRight - pageLeft) * 0.72;
    for (const rx of [ring1X, ring2X]) {
      const rdx = x - rx;
      const rdy = y - ringY;
      if (rdx * rdx + rdy * rdy <= ringRadius * ringRadius) return dark;
    }

    return bgColor;
  };
}

const densities = {
  'mipmap-mdpi': 48,
  'mipmap-hdpi': 72,
  'mipmap-xhdpi': 96,
  'mipmap-xxhdpi': 144,
  'mipmap-xxxhdpi': 192,
};

const resDir = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res');

Object.entries(densities).forEach(([folder, size]) => {
  const dir = path.join(resDir, folder);
  fs.mkdirSync(dir, { recursive: true });
  const png = buildPng(size, size, makeDrawIcon(size));
  fs.writeFileSync(path.join(dir, 'ic_launcher.png'), png);
  fs.writeFileSync(path.join(dir, 'ic_launcher_round.png'), png);
  console.log(`wrote ${folder} (${size}x${size})`);
});
