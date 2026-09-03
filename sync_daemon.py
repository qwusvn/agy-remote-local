import os
import sys
import glob
import json
import sqlite3
import shutil
import time
import re

if sys.stdout:
    sys.stdout.reconfigure(encoding='utf-8')

PC_STORAGE = r'C:\Users\qwusv\AppData\Roaming\Antigravity\app_storage.json'
PROJECTS_DIR = r'C:\Users\qwusv\.gemini\config\projects'
ANNOTATIONS_SRC = r'C:\Users\qwusv\.gemini\antigravity\annotations'
ANNOTATIONS_DST = r'C:\Users\qwusv\.gemini\antigravity-cli\annotations'
CONVOS_SRC = r'C:\Users\qwusv\.gemini\antigravity\conversations'
SUMMARIES_PB_SRC = r'C:\Users\qwusv\.gemini\antigravity\agyhub_summaries_proto.pb'
SUMMARIES_PB_DST = r'C:\Users\qwusv\.gemini\antigravity-cli\agyhub_summaries_proto.pb'
CLI_DB = r'C:\Users\qwusv\.gemini\antigravity-cli\conversation_summaries.db'

def parse_pbtxt(filepath):
    title = None
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
            m = re.search(r'title:\s*"([^"]+)"', content)
            if m:
                title = m.group(1)
    except Exception:
        pass
    return title

def get_project_mappings():
    folder_to_pid = {}
    pid_to_name = {}
    for pf in glob.glob(os.path.join(PROJECTS_DIR, "*.json")):
        try:
            with open(pf, 'r', encoding='utf-8') as f:
                d = json.load(f)
                pid = d.get("id")
                name = d.get("name")
                if pid:
                    pid_to_name[pid] = name
                    for res in d.get("projectResources", {}).get("resources", []):
                        f_uri = res.get("folderUri") or res.get("gitFolder", {}).get("folderUri")
                        if f_uri:
                            f_path = f_uri.replace("file:///", "").replace("file://", "").replace("/", "\\").lower()
                            folder_to_pid[f_path] = pid
        except Exception:
            pass
    return folder_to_pid, pid_to_name

def sync_once():
    # 1. Đồng bộ PB summaries
    if os.path.exists(SUMMARIES_PB_SRC):
        try:
            if not os.path.exists(SUMMARIES_PB_DST) or os.path.getmtime(SUMMARIES_PB_SRC) > os.path.getmtime(SUMMARIES_PB_DST):
                shutil.copy2(SUMMARIES_PB_SRC, SUMMARIES_PB_DST)
        except Exception:
            pass

    # 2. Đồng bộ annotations
    if os.path.exists(ANNOTATIONS_SRC):
        os.makedirs(ANNOTATIONS_DST, exist_ok=True)
        for f in os.listdir(ANNOTATIONS_SRC):
            if f.endswith('.pbtxt'):
                s = os.path.join(ANNOTATIONS_SRC, f)
                d = os.path.join(ANNOTATIONS_DST, f)
                try:
                    if not os.path.exists(d) or os.path.getmtime(s) > os.path.getmtime(d):
                        shutil.copy2(s, d)
                except Exception:
                    pass

    # 3. Quét project mapping
    folder_to_pid, pid_to_name = get_project_mappings()

    # 4. Quét tất cả conversations db
    if not os.path.exists(CLI_DB):
        return

    conn = sqlite3.connect(CLI_DB)
    c = conn.cursor()

    # Đọc danh sách conversation_id hiện có trong CLI DB
    c.execute("SELECT conversation_id, title, project_id FROM conversation_summaries")
    existing = {r[0]: (r[1], r[2]) for r in c.fetchall()}

    # Quét annotations để lấy title và mtime
    convo_titles = {}
    if os.path.exists(ANNOTATIONS_SRC):
        for f in os.listdir(ANNOTATIONS_SRC):
            if f.endswith('.pbtxt'):
                cid = f[:-6]
                t = parse_pbtxt(os.path.join(ANNOTATIONS_SRC, f))
                if t:
                    convo_titles[cid] = t

    # Duyệt qua các file conversation .db ở SRC
    if os.path.exists(CONVOS_SRC):
        for f in os.listdir(CONVOS_SRC):
            if f.endswith('.db') and not f.endswith('-wal') and not f.endswith('-shm'):
                cid = f[:-3]
                if len(cid) == 36: # uuid
                    db_path = os.path.join(CONVOS_SRC, f)
                    mtime = os.path.getmtime(db_path)
                    iso_time = time.strftime('%Y-%m-%dT%H:%M:%S.000000+00:00', time.gmtime(mtime))
                    
                    title = convo_titles.get(cid)
                    
                    # Xác định project_id
                    project_id = ""
                    ws_uri_str = "[]"
                    try:
                        c_convo = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
                        cc = c_convo.cursor()
                        cc.execute("SELECT name FROM sqlite_master WHERE type='table'")
                        tbls = [t[0] for t in cc.fetchall()]
                        if "sessions" in tbls:
                            cc.execute("SELECT workspace_uri, title FROM sessions LIMIT 1")
                            s_row = cc.fetchone()
                            if s_row:
                                ws_uri = s_row[0] or ""
                                if ws_uri:
                                    ws_uri_str = json.dumps([ws_uri])
                                if not title and s_row[1]:
                                    title = s_row[1]
                                ws_clean = ws_uri.replace("file:///", "").replace("file://", "").replace("/", "\\").lower()
                                for fpath, pid in folder_to_pid.items():
                                    if fpath in ws_clean or ws_clean in fpath:
                                        project_id = pid
                                        break
                        c_convo.close()
                    except Exception:
                        pass

                    if not title:
                        if cid in existing and existing[cid][0]:
                            title = existing[cid][0]
                        else:
                            title = "Cuộc trò chuyện mới"

                    needs_update = False
                    if cid not in existing:
                        needs_update = True
                    else:
                        old_title, old_pid = existing[cid]
                        if (title and old_title != title) or (project_id and old_pid != project_id):
                            needs_update = True

                    if needs_update:
                        c.execute("""
                            INSERT OR REPLACE INTO conversation_summaries (
                                conversation_id, title, preview, step_count, last_modified_time,
                                workspace_uris, status, source, project_id, agent_name,
                                last_user_input_time
                            ) VALUES (?, ?, ?, 10, ?, ?, 'COMPLETED', 'USER', ?, 'AGY', ?)
                        """, (
                            cid,
                            title,
                            title,
                            iso_time,
                            ws_uri_str,
                            project_id,
                            iso_time
                        ))

    conn.commit()
    conn.close()

if __name__ == "__main__":
    print("[SYNC DAEMON] Bắt đầu đồng bộ chi tiết...")
    sync_once()
    print("[SYNC DAEMON] Hoàn tất đồng bộ ban đầu.")
    if len(sys.argv) > 1 and sys.argv[1] == "--once":
        sys.exit(0)
    while True:
        try:
            sync_once()
        except Exception as e:
            pass
        time.sleep(2)
