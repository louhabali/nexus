#!/bin/bash

mkdir -p ~/.config/docker

cat > ~/.config/docker/daemon.json <<EOF
{
  "insecure-registries": ["localhost:8091"]
}
EOF

systemctl --user restart docker

echo "Done. Docker now accepts HTTP registry: localhost:8091"