https://github.com/etcd-io/etcd/tree/master/hack/kubernetes-deploy

```bash 
kubectl create deployment recipe --image=pambrose/etcd-recipes-k8s-example:1.0.9

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