#!/bin/bash

mkdir -p ~/.config/docker

cat > ~/.config/docker/daemon.json <<EOF
{
  "insecure-registries": ["nexus:8091"]
}
EOF

systemctl --user restart docker

echo "Done. Docker now accepts HTTP registry: nexus:8091"