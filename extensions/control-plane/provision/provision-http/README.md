# provision-http

Execute arbitrary code in a external http backend in provision phase bevor transfer.
Creation or preparation of data that will be transfererd in the following transfer phase.
Provision phase can take an arbitrary amount of time to complete.
The end of provisioning is indicated by the execution of a callback.
With this callback the DataAddress can be specified, from which the provider connector the data will load.

## Prerequisite

**Build Dependency**:

```
implementation(project(":extensions:control-plane:provision:provision-http"))
```

**Static Configuration**

```bash
# hostname is required as callback address for provisioner-backend
edc.hostname=localhost

provisioner.http.entries.default.provisioner.type=provider
provisioner.http.entries.default.endpoint=http://localhost:8000/provision
provisioner.http.entries.default.data.address.type=HttpProvision
```

Static Configuration to register a provisioner.
Multiple provisioner can be defined by set a new entry name (switch "default"), and adapt `endpoint` and `data.address.type`.
The resolution of the used provisioner will only be determined by matching values of `data.address.type` in the config and `dataAddress.properties.type` in the asset.
At the moment no proxy of values for header, path or query in the asses `dataAddress.properties` to the backend provisioner implemented.


**Provisioner Asset**

```bash
curl --location --request POST 'http://localhost:8182/api/v1/management/assets' \
--header 'x-api-key: password' \
--header 'Content-Type: application/json' \
--data-raw '{
  "asset": {
    "properties": {
      "asset:prop:id": "demo-asset-1",
      "asset:prop:name": "test-demo-asset",
      "asset:prop:contenttype": "application/json",
      "asset:prop:policy-id": "use-eu"
    }
  },
  "dataAddress": {
    "properties": {
      "type": "HttpProvision"

    },
    "transferType": {
    "contentType": "application/octet-stream",
    "isFinite": true
    }
  }
}'
```

## Provisioner Backend

Example Provisioner Backend in Python

```python
from pydantic import BaseModel
import requests
import logging
from threading import Thread
from fastapi import FastAPI, Request

app = FastAPI()


class EdcRequest(BaseModel):
    assetId: str
    transferProcessId: str
    callbackAddress: str
    resourceDefinitionId: str
    policy: dict


def task(edcRequest: EdcRequest):

    #######################
    ## LONG RUNNING TASK ##
    #######################

    data = {
        "edctype": "dataspaceconnector:provisioner-callback-request",
        "resourceDefinitionId": edcRequest.resourceDefinitionId,
        "assetId": edcRequest.assetId,
        "resourceName": aName,
        "contentDataAddress": {
            "properties": {
                "type": "HttpData",
                "baseUrl", "http://localhost:8000/data/"
            }
        },
        "apiKeyJwt": "unused",
        "hasToken": False
    }
    completeUrl = f"{edcRequest.callbackAddress}/{edcRequest.transferProcessId}/provision"

    resp = requests.post(url=completeUrl, json=data,
                         headers={"x-api-key": PROVISIONER_API_KEY})


@app.post("/provision/")
async def provision(edcRequest: EdcRequest):
    t = Thread(target=task,
               kwargs={
                   "edcRequest": edcRequest}
               )
    t.start()
    return {}


@app.get("/data/")
async def getData():
    return {"message": "I am provider"}
```