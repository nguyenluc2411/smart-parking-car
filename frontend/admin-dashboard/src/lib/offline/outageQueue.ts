import type { Gate, QueuedOutageEvent } from "@/types";

const DB_NAME = "smart-parking-outage";
const DB_VERSION = 1;
const EVENT_STORE = "events";
const CACHE_STORE = "cache";
const GATES_KEY = "gates";

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(EVENT_STORE)) {
        db.createObjectStore(EVENT_STORE, { keyPath: "clientEventId" });
      }
      if (!db.objectStoreNames.contains(CACHE_STORE)) {
        db.createObjectStore(CACHE_STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function transactionDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

export async function putOutageEvent(event: QueuedOutageEvent): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(EVENT_STORE, "readwrite");
  tx.objectStore(EVENT_STORE).put(event);
  await transactionDone(tx);
  db.close();
}

export async function listOutageEvents(): Promise<QueuedOutageEvent[]> {
  const db = await openDb();
  const events = await requestResult(
    db.transaction(EVENT_STORE, "readonly").objectStore(EVENT_STORE).getAll()
  );
  db.close();
  return (events as QueuedOutageEvent[]).sort(
    (a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime()
  );
}

export async function deleteOutageEvent(clientEventId: string): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(EVENT_STORE, "readwrite");
  tx.objectStore(EVENT_STORE).delete(clientEventId);
  await transactionDone(tx);
  db.close();
}

export async function cacheGates(gates: Gate[]): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(CACHE_STORE, "readwrite");
  tx.objectStore(CACHE_STORE).put(gates, GATES_KEY);
  await transactionDone(tx);
  db.close();
}

export async function getCachedGates(): Promise<Gate[]> {
  const db = await openDb();
  const gates = await requestResult(
    db.transaction(CACHE_STORE, "readonly").objectStore(CACHE_STORE).get(GATES_KEY)
  );
  db.close();
  return (gates as Gate[] | undefined) ?? [];
}
