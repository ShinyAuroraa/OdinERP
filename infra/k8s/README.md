# Kubernetes — WMS Odin ERP (Local Kind)

Quick-start para rodar o WMS Frontend em cluster `kind` local.

## Pré-requisitos

- [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Docker](https://docs.docker.com/get-docker/)

## 1. Criar cluster kind

```bash
kind create cluster --name odin
```

## 2. Instalar nginx ingress controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Aguardar ingress ficar pronto
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

## 3. Criar o Secret (antes do apply)

```bash
kubectl create namespace wms 2>/dev/null || true

kubectl create secret generic wms-web-secrets \
  --from-literal=NEXTAUTH_SECRET="$(openssl rand -base64 32)" \
  --namespace=wms
```

## 4. Apply dos manifestos

```bash
kubectl apply -k infra/k8s/wms-web/
```

## 5. Configurar /etc/hosts

```bash
# Linux/macOS
echo "127.0.0.1 wms.local" | sudo tee -a /etc/hosts

# Windows (PowerShell como Admin)
Add-Content C:\Windows\System32\drivers\etc\hosts "127.0.0.1 wms.local"
```

## 6. Verificar

```bash
kubectl get pods -n wms
kubectl get ingress -n wms
```

Abrir no browser: `http://wms.local`

## Atualizar imagem

```bash
# Depois de push para ghcr.io
kubectl set image deployment/wms-web \
  wms-web=ghcr.io/shinuauroraa/odin-wms-web:latest \
  --namespace=wms

kubectl rollout status deployment/wms-web --namespace=wms
```

## Estrutura

```
infra/k8s/wms-web/
├── kustomization.yaml   # kubectl apply -k
├── namespace.yaml       # namespace: wms
├── configmap.yaml       # variáveis não-sensíveis
├── secret.yaml          # template — criar manualmente
├── deployment.yaml      # 2 réplicas, rolling update
├── service.yaml         # ClusterIP port 80 → 3000
└── ingress.yaml         # nginx, host: wms.local
```
