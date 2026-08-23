function readPackage(pkg, context) {
  // Force nanoid update to fix CVE-2026-67213
  if (pkg.dependencies && pkg.dependencies.nanoid) {
    pkg.dependencies.nanoid = '^3.3.18'
    context.log(`Overriding nanoid version in ${pkg.name}`)
  }
  return pkg
}

module.exports = {
  hooks: {
    readPackage
  }
}
