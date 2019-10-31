
```bash 
kubectl create deployment recipe --image=pambrose/etcd-recipes-k8s-example:1.0.10

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
kubectl create deployment recipe --image=pambrose/etcd-recipes-k8s-example:1.0.10
kubectl expose deployment recipe --type=LoadBalancer --port=8080
kubectl scale deployment recipe --replicas=3
```

## Installing etcd
https://github.com/etcd-io/etcd/tree/master/hack/kubernetes-deploy
```bash
kubectl create -f ~/git/etcd/hack/kubernetes-deploy/etcd.yml
```

## Installing kiali
https://stackoverflow.com/questions/23620827/envsubst-command-not-found-on-mac-os-x-10-8

```bash 
bash <(curl -L https://git.io/getLatestKialiOperator) --accessible-namespaces '**'

kubectl expose deployment kiali-operator --type=NodePort --port=8081

```