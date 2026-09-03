"""
AGY LAN Bridge v3.0 (Full Continuous Header Rewriter)
Chuyen tiep lien tuc va tu dong sua Host: 127.0.0.1:4400 cho TAT CA cac HTTP requests (Keep-Alive)
va WebSocket Handshake den tu thiet bi di dong / Android.
"""
import socket
import threading
import re
import sys

LOCAL_TARGET_HOST = "127.0.0.1"
LOCAL_TARGET_PORT = 4400
LISTEN_PORT = 4400

HOST_PATTERN = re.compile(rb"(?i)\r\nHost:[^\r\n]+")
ORIGIN_PATTERN = re.compile(rb"(?i)\r\nOrigin:[^\r\n]+")
REPLACEMENT_HOST = f"\r\nHost: {LOCAL_TARGET_HOST}:{LOCAL_TARGET_PORT}".encode("latin1")
REPLACEMENT_ORIGIN = f"\r\nOrigin: http://{LOCAL_TARGET_HOST}:{LOCAL_TARGET_PORT}".encode("latin1")

def get_lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "192.168.1.220"

def client_to_server(client_sock, server_sock):
    is_websocket = False
    try:
        while True:
            data = client_sock.recv(65536)
            if not data:
                break

            if not is_websocket:
                # Kiem tra xem co chua header Host khong va rewrite lien tuc cho moi HTTP request trong connection
                if b"\r\nHost:" in data or b"\r\nhost:" in data:
                    data = HOST_PATTERN.sub(REPLACEMENT_HOST, data)
                    if b"\r\nOrigin:" in data or b"\r\norigin:" in data:
                        data = ORIGIN_PATTERN.sub(REPLACEMENT_ORIGIN, data)

                    if b"Upgrade: websocket" in data or b"upgrade: websocket" in data:
                        is_websocket = True

            server_sock.sendall(data)
    except Exception:
        pass
    finally:
        try: client_sock.close()
        except: pass
        try: server_sock.close()
        except: pass

def server_to_client(server_sock, client_sock):
    try:
        while True:
            data = server_sock.recv(65536)
            if not data:
                break
            client_sock.sendall(data)
    except Exception:
        pass
    finally:
        try: server_sock.close()
        except: pass
        try: client_sock.close()
        except: pass

def handle_client(client_sock):
    try:
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.connect((LOCAL_TARGET_HOST, LOCAL_TARGET_PORT))

        t1 = threading.Thread(target=client_to_server, args=(client_sock, server_sock), daemon=True)
        t2 = threading.Thread(target=server_to_client, args=(server_sock, client_sock), daemon=True)
        t1.start()
        t2.start()
    except Exception:
        try: client_sock.close()
        except: pass

def main():
    lan_ip = get_lan_ip()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server.bind((lan_ip, LISTEN_PORT))
    except Exception as e:
        print(f"Khong the bind {lan_ip}:{LISTEN_PORT}: {e}")
        return

    server.listen(256)
    print("=" * 60)
    print(" [AGY LAN BRIDGE V3.0 DA SAN SANG]")
    print(f" -> Dia chi LAN: http://{lan_ip}:{LISTEN_PORT}")
    print(f" -> Chuyen tiep: http://{LOCAL_TARGET_HOST}:{LOCAL_TARGET_PORT}")
    print(" -> Che do: Continuous Header Rewrite (Pass 100% Localhost Only)")
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
