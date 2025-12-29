with import <nixpkgs> { };
mkShell {
  buildInputs = [
    jq

    libopus
    pkg-config
  ] ++ lib.optionals stdenv.hostPlatform.isDarwin [ darwin.libiconv gnused ];
}
