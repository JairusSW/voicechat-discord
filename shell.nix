with import <nixpkgs> { };
mkShell {
  buildInputs = [
    jq

    libopus
    pkg-config

    (python3.withPackages (ps: with ps; [ jinja2 ]))
  ] ++ lib.optionals stdenv.hostPlatform.isDarwin [ darwin.libiconv gnused ];
}
