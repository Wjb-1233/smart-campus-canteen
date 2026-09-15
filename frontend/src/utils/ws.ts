import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface StompHandlers {
  /** 连接成功后订阅各自主题 */
  onConnect?: (client: Client) => void
  onDisconnect?: () => void
}

/** 建立 SockJS + STOMP 连接（走 Vite 代理 /api/ws → 后端 /ws） */
export function openStomp(handlers: StompHandlers = {}): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS('/api/ws') as any,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => handlers.onConnect?.(client),
    onWebSocketClose: () => handlers.onDisconnect?.(),
    onStompError: () => handlers.onDisconnect?.(),
  })
  client.activate()
  return client
}
