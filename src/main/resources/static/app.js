const { useEffect, useState } = React;

const API_BASE = "/api/v1/friends";

function FriendForm({ draft, onChange, onSubmit, onCancel, editing }) {
  return (
    <form className="card" onSubmit={onSubmit}>
      <h2>{editing ? "Update Friend" : "Add Friend"}</h2>
      <label>
        Name
        <input
          required
          value={draft.name}
          onChange={(e) => onChange({ ...draft, name: e.target.value })}
        />
      </label>
      <label>
        Email
        <input
          required
          type="email"
          value={draft.email}
          onChange={(e) => onChange({ ...draft, email: e.target.value })}
        />
      </label>
      <label>
        Phone
        <input
          value={draft.phone}
          onChange={(e) => onChange({ ...draft, phone: e.target.value })}
        />
      </label>
      <div className="actions">
        <button type="submit">{editing ? "Save" : "Create"}</button>
        {editing && <button type="button" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

function App() {
  const emptyDraft = { id: null, name: "", email: "", phone: "" };
  const [friends, setFriends] = useState([]);
  const [draft, setDraft] = useState(emptyDraft);
  const [error, setError] = useState("");

  // ── Search & Pagination state ──
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  async function loadFriends(p = page, q = search) {
    const params = new URLSearchParams({ page: p, q: q });
    const response = await fetch(`${API_BASE}?${params}`);
    if (!response.ok) {
      throw new Error("Cannot load friends");
    }
    const data = await response.json();
    setFriends(data.content);
    setTotalPages(data.totalPages);
    setTotalElements(data.totalElements);
    setPage(data.number);
  }

  useEffect(() => {
    loadFriends(0, "").catch((e) => setError(e.message));
  }, []);

  function handleSearchChange(e) {
    const q = e.target.value;
    setSearch(q);
    loadFriends(0, q).catch((err) => setError(err.message));
  }

  function goToPage(p) {
    loadFriends(p, search).catch((err) => setError(err.message));
  }

  async function submitForm(event) {
    event.preventDefault();
    setError("");

    const editing = Boolean(draft.id);
    const url = editing ? `${API_BASE}/${draft.id}` : API_BASE;
    const method = editing ? "PUT" : "POST";

    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: draft.name, email: draft.email, phone: draft.phone })
    });

    if (!response.ok) {
      // Translate HTTP status codes into user-friendly messages.
      // 409 Conflict     → duplicate email (unique constraint violated).
      // 400 Bad Request  → missing required field (name or email).
      // 404 Not Found    → trying to update/delete a record that no longer exists.
      // 500+             → unexpected server error (e.g. DB unreachable).
      const status = response.status;
      if (status === 409) {
        setError("Operation failed: that email address is already used by another friend.");
      } else if (status === 400) {
        setError("Operation failed: name and email are required fields.");
      } else if (status === 404) {
        setError("Operation failed: friend not found (may have been deleted).");
      } else {
        setError(`Operation failed (HTTP ${status}). Check the server logs for details.`);
      }
      return;
    }

    setDraft(emptyDraft);
    await loadFriends(page, search);
  }

  async function removeFriend(id) {
    setError("");
    const response = await fetch(`${API_BASE}/${id}`, { method: "DELETE" });
    if (!response.ok) {
      setError("Delete failed");
      return;
    }
    // se la pagina corrente resta vuota, torna indietro
    if (friends.length === 1 && page > 0) {
      await loadFriends(page - 1, search);
    } else {
      await loadFriends(page, search);
    }
  }

  function startEdit(friend) {
    setDraft(friend);
  }

  return (
    <main>
      <h1>Friends Manager</h1>
      {error && <p className="error">{error}</p>}
      <FriendForm
        draft={draft}
        onChange={setDraft}
        onSubmit={submitForm}
        onCancel={() => setDraft(emptyDraft)}
        editing={Boolean(draft.id)}
      />

      <section className="card">
        <h2>Friends {totalElements > 0 && <span className="badge">{totalElements}</span>}</h2>

        <div className="search-bar">
          <input
            type="search"
            placeholder="Search by name, email or phone…"
            value={search}
            onChange={handleSearchChange}
          />
        </div>

        {friends.length === 0 && <p>No friends found.</p>}
        <ul>
          {friends.map((friend) => (
            <li key={friend.id}>
              <div>
                <strong>{friend.name}</strong>
                <span>{friend.email}</span>
                <span>{friend.phone || "No phone"}</span>
              </div>
              <div className="actions">
                <button onClick={() => startEdit(friend)}>Edit</button>
                <button onClick={() => removeFriend(friend.id)}>Delete</button>
              </div>
            </li>
          ))}
        </ul>

        {totalPages > 1 && (
          <div className="pagination">
            <button disabled={page === 0} onClick={() => goToPage(page - 1)}>
              ← Prev
            </button>
            <span>Page {page + 1} of {totalPages}</span>
            <button disabled={page >= totalPages - 1} onClick={() => goToPage(page + 1)}>
              Next →
            </button>
          </div>
        )}
      </section>
    </main>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
