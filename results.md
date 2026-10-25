| check | result | detail |
|---|---|---|
| network reachable without the app | pass | ip=20.169.53.62 warp=off |
| app opens | pass |  |
| MASQUE connects (VPN mode) | pass | in 122s |
| traffic and DNS go through the VPN | pass | ip=104.28.205.117 warp=on loc=US dns=ok |
| SOCKS5 proxy works while in VPN mode | pass | ip=104.28.205.117 |
| disconnects | pass |  |
| WireGuard connects (proxy mode) (optional) | pass | in 13s |
| WireGuard proxy carries traffic (optional) | pass | ip=104.28.205.117 |
| Psiphon connects (VPN mode) (optional) | pass | in 2s |
| Psiphon carries VPN traffic (optional) | pass | ip=217.138.222.83 loc=IE |
| Tor connects (proxy mode) (optional) | pass | in 29s |
| Tor proxy carries traffic (optional) | pass | ip=37.114.50.18 loc=T1 |
