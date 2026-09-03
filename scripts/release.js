const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const root = path.join(__dirname, '..');
const pkgPath = path.join(root, 'package.json');
const gradlePath = path.join(root, 'android', 'app', 'build.gradle.kts');

const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
const [major, minor, patch] = pkg.version.split('.').map(Number);
const newVersion = `${major}.${minor}.${patch + 1}`;

pkg.version = newVersion;
fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n', 'utf-8');

let gradle = fs.readFileSync(gradlePath, 'utf-8');
const versionCodeMatch = gradle.match(/versionCode = (\d+)/);
const newVersionCode = versionCodeMatch ? parseInt(versionCodeMatch[1], 10) + 1 : 1;
gradle = gradle.replace(/versionCode = \d+/, `versionCode = ${newVersionCode}`);
gradle = gradle.replace(/versionName = "[^"]*"/, `versionName = "${newVersion}"`);
fs.writeFileSync(gradlePath, gradle, 'utf-8');

console.log(`Version bumped: ${pkg.version} (Electron), ${newVersion} / versionCode ${newVersionCode} (Android)`);

execSync(`git add "${pkgPath}" "${gradlePath}"`, { stdio: 'inherit', cwd: root });
execSync(`git commit -m "chore: release v${newVersion}"`, { stdio: 'inherit', cwd: root });
execSync(`git tag v${newVersion}`, { stdio: 'inherit', cwd: root });

console.log(`\nTagged v${newVersion} locally. Review, then push to trigger the release build:`);
console.log(`  git push origin main --tags`);
