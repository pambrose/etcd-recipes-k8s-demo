# etcd-recipes K8s Examples

## Setup minkube
```bash
minikube start --memory=4096 --disk-size=30g --kubernetes-version=v1.16.2
minikube dashboard
minikube stop
minikube delete

eval $(minikube docker-env)
```

## Install etcd
https://github.com/etcd-io/etcd/tree/master/hack/kubernetes-deploy
```bash
kubectl create -f https://raw.githubusercontent.com/etcd-io/etcd/master/hack/kubernetes-deploy/etcd.yml
```

## Install recipes with scripts
```bash
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-admin.yaml
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-election.yaml
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-counter.yaml

minikube service admin-service

kubectl delete deployment admin-deploy
kubectl delete deployment election-deploy
kubectl delete deployment counter-deploy

kubectl delete service admin-service
kubectl delete service election-service
kubectl delete service counter-service
```

## View logs
```bash
kubectl logs pod_name
```

## Edit a deployment
```bash
kubectl edit deployment/admin-deploy
kubectl edit deployment/election-deploy
kubectl edit deployment/counter-deploy
```

## Install istio
https://istio.io/docs/setup/install/kubernetes/
```bash
cd istio-1.3.3
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
kubectl apply -f install/kubernetes/istio-demo.yaml
```

## Install kiali
https://stackoverflow.com/questions/23620827/envsubst-command-not-found-on-mac-os-x-10-8
```bash 
bash <(curl -L https://git.io/getLatestKialiOperator) --accessible-namespaces '**'

kubectl expose deployment kiali-operator --type=NodePort --port=8081

```

## Connect to a Pod
```bash
kubectl exec -it pod_name -- /bin/bash
kubectl exec pod_name -c shell -i -t -- /bin/bash
```

## Supported API versions
```bash
kubectl api-versions
```

## Links
* [Creating a Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
* [Service Discovery](http://kubernetesbyexample.com/sd/)

```bash 

kubectl create deployment etcd-admin --image=pambrose/etcd-admin:1.0.2
kubectl expose deployment etcd-admin --type=LoadBalancer --port=8080
kubectl scale deployment etcd-admin --replicas=3

kubectl expose deployment etcd-admin --type=NodePort --port=8080
kubectl expose deployment etcd-admin --type=LoadBalancer --port=8080

kubectl delete deployment etcd-admin
kubectl delete service etcd-admin

kubectl scale deployment etcd-admin --replicas=3
```