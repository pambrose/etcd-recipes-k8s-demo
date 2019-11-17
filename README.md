# etcd-recipes K8s Examples

## Install etcd
```bash
kubectl create -f https://raw.githubusercontent.com/etcd-io/etcd/master/hack/kubernetes-deploy/etcd.yml
```

## Install etcd-recipes 
```bash
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-admin.yaml
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-election.yaml
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-counter.yaml
```

## Edit etcd-recipes 
```bash
kubectl edit deployment/admin-deploy 
kubectl edit deployment/election-deploy
kubectl edit deployment/counter-deploy

kubectl edit service/admin-service 
kubectl edit service/election-service
kubectl edit service/counter-service
```

## Uninstall etcd-recipes 
```bash
kubectl delete deployment admin-deploy
kubectl delete deployment election-deploy
kubectl delete deployment counter-deploy

kubectl delete service admin-service
kubectl delete service election-service
kubectl delete service counter-service
```

## Install prometheus-proxy 
```bash
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-prometheus-agent.yaml
```
## Edit prometheus-proxy 
```bash
kubectl edit deployment/agent-deploy 

kubectl edit service/agent-service 
```

## Uninstall prometheus-proxy 
```bash
kubectl delete deployment agent-deploy

kubectl delete service agent-service
```

## Install network policy 
```bash
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/allow-all-egress.yaml
```
## Edit network policy 
```bash
kubectl edit networkpolicy/allow-all 
```

## Uninstall network policy 
```bash
kubectl delete networkpolicy allow-all
```