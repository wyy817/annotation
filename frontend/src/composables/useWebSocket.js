import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// Singleton STOMP client shared across all components
let stompClient = null

export function useWebSocket() {
  /**
   * Connect to the WebSocket server and set up subscriptions.
   * @param {string} token  - JWT token (appended as query param)
   * @param {Array<{destination: string, callback: Function}>} subscriptions
   */
  function connect(token, subscriptions) {
    if (stompClient?.active) return

    stompClient = new Client({
      webSocketFactory: () => new SockJS(`/ws?token=${token}`),
      reconnectDelay: 5000,
      onConnect: () => {
        subscriptions.forEach(({ destination, callback }) => {
          stompClient.subscribe(destination, (frame) => {
            try {
              callback(JSON.parse(frame.body))
            } catch (e) {
              console.warn('[WS] Failed to parse message:', e)
            }
          })
        })
      },
      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message'])
      },
    })

    stompClient.activate()
  }

  function disconnect() {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
  }

  function isConnected() {
    return stompClient?.connected ?? false
  }

  return { connect, disconnect, isConnected }
}
