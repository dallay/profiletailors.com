export function computeBuildInfo(pkgJsonPath: string): {
  version: string;
  gitSha: string;
  buildTime: string;
};
