"""
AGY LAN Bridge v2.0
Tu dong rewrite Host va Origin sang 127.0.0.1:4400 de vuot qua co che bao mat
Localhost-Only cua may chu Antigravity 2.0.
Ho tro 100% Web UI, REST API, SSE, Image Upload va WebSocket hai chieu.
"""
import socket
import threading
import re
import sys

LOCAL_TARGET_HOST = "127.0.0.1"
LOCAL_TARGET_PORT = 4400
LISTEN_PORT = 4400

def get_lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "192.168.1.220"

def forward_raw(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try: src.close()
        except: pass
        try: dst.close()
        except: pass

def handle_client(client_socket):
    server_socket = None
    try:
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.connect((LOCAL_TARGET_HOST, LOCAL_TARGET_PORT))

        # Doc header khoi tao tu client
        initial_data = client_socket.recv(65536)
        if not initial_data:
            client_socket.close()
            server_socket.close()
            return

        # Rewrite Host va Origin header ve localhost de pass qua auth check
        try:
            text = initial_data.decode("latin1")
            # Thay the Host: ... thanh Host: 127.0.0.1:4400
            text = re.sub(r"(?i)\r\nHost:[^\r\n]+", f"\r\nHost: 127.0.0.1:{LOCAL_TARGET_PORT}", text)
            # Thay the Origin: ... thanh Origin: http://127.0.0.1:4400 neu co
            text = re.sub(r"(?i)\r\nOrigin:[^\r\n]+", f"\r\nOrigin: http://127.0.0.1:{LOCAL_TARGET_PORT}", text)
            modified_data = text.encode("latin1")
            server_socket.sendall(modified_data)
        except Exception:
            server_socket.sendall(initial_data)

        # Chuyen tiep hai chieu duplex (WebSockets / Streams)
        t1 = threading.Thread(target=forward_raw, args=(client_socket, server_socket), daemon=True)
        t2 = threading.Thread(target=forward_raw, args=(server_socket, client_socket), daemon=True)
        t1.start()
        t2.start()
    except Exception as e:
        if client_socket:
            try: client_socket.close()
            except: pass
        if server_socket:
            try: server_socket.close()
            except: pass

def main():
    lan_ip = get_lan_ip()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server.bind((lan_ip, LISTEN_PORT))
    except Exception as e:
        print(f"Loi khi bind vao {lan_ip}:{LISTEN_PORT}: {e}")
        try:
            server.bind(("0.0.0.0", 4401))
            lan_ip = "0.0.0.0"
            print("Fallback sang cong 4401")
        except Exception as e2:
            print(f"Khong the mo cong: {e2}")
            return

    server.listen(256)
    print("=" * 60)
    print(" [AGY LAN BRIDGE V2.0 DA SAN SANG]")
    print(f" -> IP ket noi: http://{lan_ip}:{LISTEN_PORT}")
    print(f" -> Rewrite Host: 127.0.0.1:{LOCAL_TARGET_PORT}")
    print(f" -> Trang thai: HOAT DONG")
    print("=" * 60)
    sys.stdout.flush()

    while True:
        try:
            sock, _ = server.accept()
            threading.Thread(target=handle_client, args=(sock,), daemon=True).start()
        except KeyboardInterrupt:
            break
        except Exception:
            pass

if __name__ == "__main__":
    main()
