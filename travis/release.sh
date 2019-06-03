openssl aes-256-cbc -K $encrypted_c1f2aadc729a_key -iv $encrypted_c1f2aadc729a_iv -in secrets.tar.enc -out local.secrets.tar -d
tar xv -C travis -f local.secrets.tar
sbt +publishSigned sonatypeRelease
