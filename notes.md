# Links
* [Service Discovery](http://kubernetesbyexample.com/sd/)

```bash 
kubectl create deployment recipe --image=pambrose/etcd-recipes-k8s-example:1.0.13

kubectl expose deployment recipe --type=NodePort --port=8080
kubectl expose deployment recipe --type=LoadBalancer --port=8080

eval $(minikube docker-env)

kubectl delete deployment recipe
kubectl delete service recipe

kubectl scale deployment recipe --replicas=3

// Open url for service
minikube service recipe
minikube service recipe --url
```

## Creating new cluster
```bash
kubectl create deployment recipe --image=pambrose/etcd-recipes-k8s-example:1.0.13
kubectl expose deployment recipe --type=LoadBalancer --port=8080
kubectl scale deployment recipe --replicas=3
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

## Running minkube
```bash
minikube start --memory=4096 --disk-size=30g --kubernetes-version=v1.16.2
minikube dashboard
minikube stop
minikube delete
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