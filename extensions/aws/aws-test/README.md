# AWS Test

To run AWS integration tests you will need a MinIO instance running:
```
docker run -d -p 9000:9000 -p 9001:9001 --name minio -e MINIO_ROOT_USER=root -e MINIO_ROOT_PASSWORD=password bitnami/minio:latest
```

Then set the two environment variables:
```
MINIO_ROOT_USER=root
MINIO_ROOT_PASSWORD=password
```
