# ProxyPotPs

ProxyPotPs is an Android application for managing proxy nodes and executing HTTP tasks through available proxies.

## Features

- **Proxy Node Management**: Import and manage Shadowsocks/Trojan proxy configurations via YAML
- **Node Probing**: Test proxy node availability and latency through local proxy services
- **Task Scheduling**: Execute HTTP tasks (GET/POST) through available proxy nodes with failover support
- **UI-based Task Configuration**: Configure and dispatch tasks directly through the user interface

## Usage

1. **Import Proxy Configuration**: Paste or import your proxy configuration YAML in Settings
2. **Probe Nodes**: Test node availability and view latency statistics
3. **Execute Tasks**: Use the "手动添加任务" (Manual Task) button in the Work screen to configure and run HTTP tasks
4. **View Results**: Monitor task execution progress, success rates, and detailed results

## Architecture

- Built with Kotlin and Jetpack Compose
- Clean Architecture with MVVM pattern
- Local proxy services for each remote node
- All network traffic routed through configured proxies
- No external API server - all operations controlled through UI