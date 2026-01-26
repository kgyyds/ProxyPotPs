# ProxyPotPs

## Local API

Start the app and verify the embedded API server is up:

```bash
adb shell curl -s http://127.0.0.1:<apiPort>/health
```

Run a task via the local API:

```bash
adb shell curl -s -X POST http://127.0.0.1:<apiPort>/run \
  -H "Content-Type: application/json" \
  -d '{"mainTaskId":"demo-1","subTasks":[{"subTaskId":"sub-1","url":"http://example.com","method":"GET","params":{}}]}'
```

Replace `<apiPort>` with the value configured in Settings.
