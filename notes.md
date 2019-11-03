# Links
* [Creating a Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
* [Service Discovery](http://kubernetesbyexample.com/sd/)

```bash 

kubectl expose deployment etcd-admin --type=NodePort --port=8080
kubectl expose deployment etcd-admin --type=LoadBalancer --port=8080

eval $(minikube docker-env)

kubectl delete deployment etcd-admin
kubectl delete service etcd-admin

kubectl scale deployment etcd-admin --replicas=3

// Open url for service
minikube service etcd-admin
minikube service etcd-admin --url
```

## Running minkube
```bash
minikube start --memory=4096 --disk-size=30g --kubernetes-version=v1.16.2
minikube dashboard
minikube stop
minikube delete
```

## Installing recipes with commands
```bash
kubectl create deployment etcd-admin --image=pambrose/etcd-admin:1.0.16
kubectl create deployment etcd-election --image=pambrose/etcd-election:1.0.0

kubectl expose deployment etcd-admin --type=LoadBalancer --port=8080
kubectl expose deployment etcd-election --type=LoadBalancer --port=8080

kubectl scale deployment etcd-admin --replicas=3
kubectl scale deployment etcd-election --replicas=3
```

## Installing recipes with scripts
```bash
kubectl apply -f https://raw.githubusercontent.com/pambrose/etcd-recipes-k8s-demo/master/yaml/create-recipes.yaml
minikube service admin-service
kubectl delete deployment admin-deploy
kubectl delete deployment election-deploy
kubectl delete service admin-service
```

## View logs
```bash
kubectl logs node_name
```

## Edit a deployment
```bash
kubectl edit deploy/admin-deploy
kubectl edit deploy/election-deploy
```

## Installing etcd
https://github.com/etcd-io/etcd/tree/master/hack/kubernetes-deploy
```bash
kubectl create -f https://raw.githubusercontent.com/etcd-io/etcd/master/hack/kubernetes-deploy/etcd.yml
```

## Installing istio
https://istio.io/docs/setup/install/kubernetes/
```bash
cd istio-1.3.3
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
kubectl apply -f install/kubernetes/istio-demo.yaml
```

## Installing kiali
https://stackoverflow.com/questions/23620827/envsubst-command-not-found-on-mac-os-x-10-8
```bash 
bash <(curl -L https://git.io/getLatestKialiOperator) --accessible-namespaces '**'

kubectl expose deployment kiali-operator --type=NodePort --port=8081

```

## Connecting to a Pod
```bash
kubectl exec -it pod_name -- /bin/bash
kubectl exec pod_name -c shell -i -t -- /bin/bash
```

## Supported API versions
```bash
kubectl api-versions
```
