// =============================================================================
// app.js — Friends Manager SPA
// =============================================================================
//
// This file is the entire React application. It is loaded by index.html as a
// <script type="text/babel"> tag, which means Babel intercepts it at runtime,
// compiles JSX syntax into plain React.createElement(...) calls, and then
// executes the result in the browser. No build step, no Node.js required.
//
// Architecture overview:
//
//   App (root component)
//   ├── LoginPage          — shown when currentUser === null
//   └── main layout        — shown when currentUser !== null
//       ├── FriendForm     — add / edit form
//       └── friends list   — search, pagination, edit/delete buttons
//
// Authentication flow:
//   1. On mount, App calls GET /api/auth/me to restore an existing session.
//   2. If no session → LoginPage is rendered.
//   3. LoginPage POSTs credentials; on success the server sets JSESSIONID.
//   4. The browser sends JSESSIONID automatically on every subsequent request
//      (same-origin cookie — no manual header management needed).
//   5. If any API call returns 401 (session expired) → back to LoginPage.
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// Destructure the three React hooks we need from the global React object.
//
// React is loaded from the CDN as a UMD bundle and exposed as window.React.
// Destructuring here gives us shorter names: useState, useEffect, useCallback
// instead of React.useState, React.useEffect, React.useCallback every time.
// ─────────────────────────────────────────────────────────────────────────────
const { useEffect, useState, useCallback } = React;

// Base URL prefixes for the two API groups.
// Using constants avoids magic strings scattered across the file and makes
// it easy to change the base path (e.g. when proxying through a gateway).
const API_BASE  = "/api/v1/friends";  // protected — requires a valid session
const AUTH_BASE = "/api/auth";        // public  — login / logout / me

// =============================================================================
// apiFetch — centralised HTTP helper
// =============================================================================
//
// Thin wrapper around the native browser fetch() API that:
//   1. Always sets Content-Type: application/json (we only speak JSON).
//   2. Always sends credentials: "same-origin" so the browser includes the
//      JSESSIONID cookie on every request automatically.
//   3. Intercepts HTTP 401 responses and converts them into a thrown error
//      with a .status property so callers can distinguish 401 from other errors.
//
// Why a wrapper instead of using fetch() directly everywhere?
//   - DRY: the cookie and Content-Type header only need to be written once.
//   - Consistent 401 handling: every caller gets the same behaviour.
//   - Easy to extend: add logging, retry logic, or CSRF headers in one place.
//
// The function is async and returns a Promise<Response>.
// Callers must still check response.ok for non-401 error codes (400, 404, …).
// =============================================================================
async function apiFetch(url, options = {}) {
  const response = await fetch(url, {
    // Spread the caller's options first so they can override defaults if needed.
    ...options,

    // same-origin: send cookies (JSESSIONID) only to the same origin.
    // This is the browser default for same-origin fetch(), but we set it
    // explicitly to make the intent visible to readers.
    credentials: "same-origin",

    // Merge headers: start with Content-Type, then overlay any headers the
    // caller provided (e.g. a custom Accept header).
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });

  if (response.status === 401) {
    // Create a standard Error and attach the HTTP status code as a property.
    // Callers check `e.status === 401` to distinguish session expiry from
    // network errors or other thrown exceptions (which have no .status).
    const err = new Error("UNAUTHORIZED");
    err.status = 401;
    throw err;
  }

  // For all other status codes (200, 201, 204, 400, 404, 409, 500…) we return
  // the Response object unchanged. Callers decide what to do with response.ok.
  return response;
}

// =============================================================================
// LoginPage — component
// =============================================================================
//
// Rendered by App when currentUser === null (no active session).
//
// Props:
//   onLogin(username: string) — callback invoked after a successful login.
//                               App uses it to store the username in state,
//                               which triggers a re-render and switches to
//                               the main view.
//
// Local state:
//   username  — controlled input value (bound to the username text field)
//   password  — controlled input value (bound to the password field)
//   error     — error message string shown below the form (empty = no error)
//   loading   — true while the login request is in flight (disables the button)
//
// In React, a "controlled input" is one whose value is driven by component
// state rather than the DOM. Every keystroke calls setState (via onChange),
// which triggers a re-render, which sets input.value from state. This makes
// the form's data always available in JS without querying the DOM.
// =============================================================================
function LoginPage({ onLogin }) {
  const [username, setUsername] = useState("");   // controlled: username field
  const [password, setPassword] = useState("");   // controlled: password field
  const [error, setError]       = useState("");   // "" means "no error to show"
  const [loading, setLoading]   = useState(false);// prevents double-submit

  // ── handleSubmit ────────────────────────────────────────────────────────
  // Called when the <form> fires its "submit" event (Enter key or button click).
  //
  // e.preventDefault() stops the browser from performing a traditional form
  // submission (which would navigate away from the page and lose React state).
  //
  // We use the native fetch() here instead of apiFetch() because we need to
  // handle 401 differently (show an error message instead of throwing).
  // ────────────────────────────────────────────────────────────────────────
  async function handleSubmit(e) {
    e.preventDefault();   // prevent full-page reload
    setError("");         // clear any previous error before the new attempt
    setLoading(true);     // disable the submit button during the request

    try {
      const response = await fetch(`${AUTH_BASE}/login`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        // JSON.stringify converts the JS object to a JSON string for the body.
        body: JSON.stringify({ username, password }),
      });

      if (response.ok) {
        // 200 OK: the server validated the credentials and created a session.
        // The Set-Cookie: JSESSIONID=... header in the response instructs the
        // browser to store the cookie; all future same-origin requests will
        // include it automatically — no manual cookie management needed.
        const data = await response.json();

        // Lift the username up to App via the onLogin callback.
        // App stores it in state → re-render → LoginPage is unmounted →
        // main application view is mounted.
        onLogin(data.username);
      } else {
        // 401 Unauthorized: wrong credentials. Show a user-friendly message.
        setError("Invalid username or password.");
      }
    } catch (err) {
      // Network-level error (server down, DNS failure, CORS, …).
      setError("Network error. Make sure the server is running.");
    } finally {
      // finally always runs, regardless of success or failure, so the button
      // is always re-enabled even if an exception was thrown.
      setLoading(false);
    }
  }

  // ── JSX return ──────────────────────────────────────────────────────────
  // JSX looks like HTML but is compiled by Babel to React.createElement calls.
  // className is used instead of class because "class" is a reserved JS keyword.
  // Curly braces {} embed JavaScript expressions inside JSX.
  // ────────────────────────────────────────────────────────────────────────
  return (
    <div className="login-wrapper">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1 className="login-title">Friends Manager</h1>
        <p className="login-subtitle">Sign in to continue</p>

        {/* Conditional rendering: the <p> is only mounted when error is truthy. */}
        {error && <p className="error">{error}</p>}

        <label>
          Username
          {/* value={username} makes this a controlled input.
              onChange updates state on every keystroke → triggers re-render
              → input displays the new value from state. */}
          <input
            type="text"
            required
            autoFocus          // browser focuses this field immediately on mount
            autoComplete="username"  // hint for the browser's password manager
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>

        <label>
          Password
          <input
            type="password"    // masks the input characters
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        {/* disabled={loading} prevents double-submission while the request
            is in flight. The ternary shows a different label during loading. */}
        <button type="submit" disabled={loading} className="login-btn">
          {loading ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </div>
  );
}

// =============================================================================
// FriendForm — component
// =============================================================================
//
// Reusable form used for both creating a new friend (POST) and editing an
// existing one (PUT). The parent component (App) owns the form data in the
// "draft" state object and passes it down via props — this is the standard
// React "lifting state up" pattern.
//
// Props:
//   draft    — { id, name, email, phone } — current field values
//   onChange — callback(updatedDraft)     — called on every field change;
//              App replaces its draft state with the new object
//   onSubmit — callback(event)            — called on form submission
//   onCancel — callback()                 — called when "Cancel" is clicked
//   editing  — boolean                    — true if draft.id is set (PUT mode)
//
// The spread operator in onChange is key:
//   onChange({ ...draft, name: e.target.value })
// This creates a NEW object with all existing fields copied, then overrides
// just the changed field. React requires new object references to detect
// state changes — mutating the existing object would not trigger a re-render.
// =============================================================================
function FriendForm({ draft, onChange, onSubmit, onCancel, editing }) {
  return (
    <form className="card" onSubmit={onSubmit}>
      {/* Ternary expression: title changes based on the editing prop. */}
      <h2>{editing ? "Update Friend" : "Add Friend"}</h2>

      <label>
        Name
        <input
          required   // HTML5 validation: browser blocks submit if empty
          value={draft.name}
          onChange={(e) => onChange({ ...draft, name: e.target.value })}
        />
      </label>

      <label>
        Email
        <input
          required
          type="email"   // browser validates email format before submit
          value={draft.email}
          onChange={(e) => onChange({ ...draft, email: e.target.value })}
        />
      </label>

      <label>
        Phone
        {/* Phone is optional — no "required" attribute. */}
        <input
          value={draft.phone}
          onChange={(e) => onChange({ ...draft, phone: e.target.value })}
        />
      </label>

      <div className="actions">
        <button type="submit">{editing ? "Save" : "Create"}</button>

        {/* The Cancel button is only rendered in edit mode.
            type="button" prevents it from submitting the form. */}
        {editing && <button type="button" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}

// =============================================================================
// App — root component
// =============================================================================
//
// The top-level component rendered into <div id="root"> by ReactDOM.createRoot.
// It owns all shared application state and orchestrates authentication,
// data loading, and CRUD operations.
//
// Rendering logic (three mutually exclusive views):
//   1. checkingSession === true  → "Loading…" spinner (avoids login page flash
//                                  while the session check is still in-flight)
//   2. currentUser === null      → <LoginPage>
//   3. currentUser !== null      → main application layout
//
// State inventory:
//   currentUser     — null | string  — null means unauthenticated
//   checkingSession — boolean        — true during the initial GET /api/auth/me
//   friends         — Friend[]       — current page of friends from the API
//   draft           — Friend draft   — data bound to <FriendForm>
//   error           — string         — global error message (empty = none)
//   search          — string         — current search query
//   page            — number         — current page index (0-based)
//   totalPages      — number         — total pages reported by the API
//   totalElements   — number         — total matching records (for the badge)
// =============================================================================
function App() {
  // emptyDraft is the "blank slate" for the form.
  // id: null signals "create mode" (POST); a real id signals "edit mode" (PUT).
  const emptyDraft = { id: null, name: "", email: "", phone: "" };

  // ── Authentication state ─────────────────────────────────────────────────
  const [currentUser, setCurrentUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);

  // ── Data state ───────────────────────────────────────────────────────────
  const [friends, setFriends]             = useState([]);
  const [draft, setDraft]                 = useState(emptyDraft);
  const [error, setError]                 = useState("");
  const [search, setSearch]               = useState("");
  const [page, setPage]                   = useState(0);
  const [totalPages, setTotalPages]       = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // ── Session check on mount ───────────────────────────────────────────────
  //
  // useEffect(fn, []) runs fn exactly once, after the component is first
  // mounted to the DOM (equivalent to componentDidMount in class components).
  // The empty dependency array [] means "no dependencies — never re-run".
  //
  // Purpose: restore an existing session after a page refresh.
  // If the user already has a valid JSESSIONID cookie, GET /api/auth/me
  // returns 200 with the username → skip the login page entirely.
  // If not (401 or network error) → show the login page.
  // ─────────────────────────────────────────────────────────────────────────
  useEffect(() => {
    fetch(`${AUTH_BASE}/me`, { credentials: "same-origin" })
      // If 200 OK, parse the JSON body; otherwise return null.
      .then((res) => (res.ok ? res.json() : null))
      // If we got a username back, store it — this triggers a re-render that
      // switches the view from LoginPage to the main application.
      .then((data) => { if (data && data.username) setCurrentUser(data.username); })
      // Silently ignore errors (e.g. server is down) — the login page handles them.
      .catch(() => {})
      // Always mark the session check as done so the "Loading…" state is removed.
      .finally(() => setCheckingSession(false));
  }, []); // ← empty deps: run once on mount only

  // ── handleUnauthorized ───────────────────────────────────────────────────
  // Called by any function that catches an error with e.status === 401.
  // Setting currentUser to null triggers a re-render → LoginPage is shown.
  // The error message is displayed on the login page for user feedback.
  // ─────────────────────────────────────────────────────────────────────────
  function handleUnauthorized() {
    setCurrentUser(null);
    setError("Your session has expired. Please sign in again.");
  }

  // ── loadFriends ──────────────────────────────────────────────────────────
  //
  // Fetches a page of friends from the API, applying the current search query.
  // The API returns a Spring Data Page object:
  //   { content: Friend[], totalPages: n, totalElements: n, number: n }
  //
  // useCallback(fn, deps) memoises the function reference.
  // Without it, loadFriends would be recreated on every render, which would
  // cause the second useEffect (below) to re-run endlessly because "loadFriends"
  // would appear as a new dependency on every render.
  //
  // Parameters:
  //   p — page index (0-based, defaults to 0)
  //   q — search query string (defaults to "")
  // ─────────────────────────────────────────────────────────────────────────
  const loadFriends = useCallback(async (p = 0, q = "") => {
    // URLSearchParams serialises { page: 0, q: "alice" } → "page=0&q=alice"
    const params = new URLSearchParams({ page: p, q });
    try {
      const response = await apiFetch(`${API_BASE}?${params}`);
      if (!response.ok) throw new Error("Cannot load friends");
      const data = await response.json();

      // Spread the page data into the corresponding state variables.
      // Each setState call is batched by React 18 into a single re-render.
      setFriends(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
      setPage(data.number);          // server confirms the actual page number
    } catch (e) {
      if (e.status === 401) { handleUnauthorized(); return; }
      setError(e.message);
    }
  }, []); // ← no deps: the function never needs to be recreated

  // ── Load friends when the user logs in ───────────────────────────────────
  //
  // useEffect(fn, [currentUser, loadFriends]) re-runs fn whenever either
  // dependency changes. Practically this fires when:
  //   - currentUser changes from null to a username (after login or session restore)
  //
  // The guard "if (currentUser)" prevents a spurious load when currentUser
  // is reset to null on logout or session expiry.
  // ─────────────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (currentUser) loadFriends(0, "");
  }, [currentUser, loadFriends]);

  // ── handleLogout ─────────────────────────────────────────────────────────
  // Calls POST /api/auth/logout to invalidate the server-side session, then
  // resets all local state to its initial values.
  // We do not use apiFetch here because we want the logout to succeed even
  // if the server returns an unexpected status — local state is always cleared.
  // ─────────────────────────────────────────────────────────────────────────
  async function handleLogout() {
    await fetch(`${AUTH_BASE}/logout`, { method: "POST", credentials: "same-origin" });
    // Reset everything — setting currentUser to null triggers the switch to LoginPage.
    setCurrentUser(null);
    setFriends([]);
    setDraft(emptyDraft);
    setError("");
    setSearch("");
    setPage(0);
  }

  // ── handleSearchChange ───────────────────────────────────────────────────
  // Fired on every keystroke in the search input.
  // Immediately fetches page 0 with the new query — no debounce is applied
  // here to keep the code simple (in production, consider debouncing).
  // ─────────────────────────────────────────────────────────────────────────
  function handleSearchChange(e) {
    const q = e.target.value;
    setSearch(q);        // update the controlled input
    loadFriends(0, q);   // reload from page 0 with the new query
  }

  // ── goToPage ─────────────────────────────────────────────────────────────
  // Called by the pagination buttons. Preserves the current search query
  // so that paginating through search results works correctly.
  // ─────────────────────────────────────────────────────────────────────────
  function goToPage(p) {
    loadFriends(p, search);
  }

  // ── submitForm ───────────────────────────────────────────────────────────
  //
  // Handles both CREATE (POST) and UPDATE (PUT) depending on whether draft.id
  // is set. This single handler is passed to <FriendForm onSubmit={submitForm}>.
  //
  // HTTP method selection:
  //   draft.id is falsy (null / 0) → POST to /api/v1/friends
  //   draft.id is truthy           → PUT  to /api/v1/friends/{id}
  //
  // phone || null: converts an empty string ("") to null before sending.
  // An empty string is not the same as "no phone" in some DB configurations,
  // and the server stores null for optional fields.
  // ─────────────────────────────────────────────────────────────────────────
  async function submitForm(event) {
    event.preventDefault(); // prevent browser form submission (page reload)
    setError("");

    const editing = Boolean(draft.id); // true = PUT, false = POST
    const url     = editing ? `${API_BASE}/${draft.id}` : API_BASE;
    const method  = editing ? "PUT" : "POST";

    try {
      const response = await apiFetch(url, {
        method,
        body: JSON.stringify({
          name:  draft.name,
          email: draft.email,
          phone: draft.phone || null,  // empty string → null
        }),
      });

      if (!response.ok) {
        // Map specific HTTP status codes to user-friendly messages.
        const status = response.status;
        if      (status === 409) setError("That email address is already used by another friend.");
        else if (status === 400) setError("Name and email are required fields.");
        else if (status === 404) setError("Friend not found (may have already been deleted).");
        else                     setError(`Operation failed (HTTP ${status}).`);
        return; // stop here — do not reset the form so the user can fix the input
      }

      // Success: reset the form to the empty draft (exits edit mode)
      // and reload the list to reflect the change.
      setDraft(emptyDraft);
      await loadFriends(page, search);
    } catch (e) {
      if (e.status === 401) { handleUnauthorized(); return; }
      setError(e.message);
    }
  }

  // ── removeFriend ─────────────────────────────────────────────────────────
  //
  // Sends DELETE /api/v1/friends/{id} and reloads the list.
  //
  // Page-boundary logic: if the deleted record was the only one on the current
  // page (friends.length === 1) and we are not on page 0, go back one page.
  // Without this, after deleting the last item on page N the list would show
  // an empty page N instead of the previous one.
  // ─────────────────────────────────────────────────────────────────────────
  async function removeFriend(id) {
    setError("");
    try {
      const response = await apiFetch(`${API_BASE}/${id}`, { method: "DELETE" });
      if (!response.ok) { setError("Delete failed."); return; }

      // Smart page navigation after deletion.
      if (friends.length === 1 && page > 0) await loadFriends(page - 1, search);
      else                                   await loadFriends(page, search);
    } catch (e) {
      if (e.status === 401) { handleUnauthorized(); return; }
      setError(e.message);
    }
  }

  // ── startEdit ────────────────────────────────────────────────────────────
  // Copies a friend object into the draft state, which:
  //   1. Pre-fills <FriendForm> with the friend's current values.
  //   2. Sets draft.id to a truthy value → FriendForm switches to "edit mode".
  // ─────────────────────────────────────────────────────────────────────────
  function startEdit(friend) { setDraft(friend); }

  // ── Render ───────────────────────────────────────────────────────────────
  //
  // React calls this function every time any piece of state changes.
  // The return value is JSX — Babel compiles it to React.createElement calls.
  // React diffs the new virtual DOM against the previous one and applies
  // only the necessary real DOM mutations (reconciliation).
  // ─────────────────────────────────────────────────────────────────────────

  // Guard 1: session check still in progress — avoid a flash of the login page.
  if (checkingSession) {
    return <div className="session-check">Loading…</div>;
  }

  // Guard 2: no authenticated user — render the login page.
  // The onLogin prop is an inline arrow function that calls setCurrentUser,
  // which triggers a re-render and exits this branch.
  if (!currentUser) {
    return <LoginPage onLogin={(username) => setCurrentUser(username)} />;
  }

  // Main view: the user is authenticated.
  return (
    <main>
      {/* ── App header: title + logged-in user + sign-out button ─────────── */}
      <div className="app-header">
        <h1>Friends Manager</h1>
        <div className="user-info">
          <span>👤 {currentUser}</span>
          {/* onClick is a React synthetic event — equivalent to addEventListener("click"). */}
          <button className="logout-btn" onClick={handleLogout}>Sign out</button>
        </div>
      </div>

      {/* Global error banner — only rendered when error is non-empty. */}
      {error && <p className="error">{error}</p>}

      {/* ── Create / Edit form ───────────────────────────────────────────── */}
      {/* All form data lives in the "draft" state here in App.
          FriendForm is a "controlled component" — it receives values and
          callbacks as props and never manages its own data state. */}
      <FriendForm
        draft={draft}
        onChange={setDraft}                     // setDraft is the raw state setter
        onSubmit={submitForm}
        onCancel={() => setDraft(emptyDraft)}   // exits edit mode
        editing={Boolean(draft.id)}             // true when draft has a real id
      />

      {/* ── Friends list ─────────────────────────────────────────────────── */}
      <section className="card">
        {/* Badge: inline conditional — only renders <span> when count > 0. */}
        <h2>Friends {totalElements > 0 && <span className="badge">{totalElements}</span>}</h2>

        {/* Search bar: controlled input, fires loadFriends on every keystroke. */}
        <div className="search-bar">
          <input
            type="search"
            placeholder="Search by name, email or phone…"
            value={search}
            onChange={handleSearchChange}
          />
        </div>

        {friends.length === 0 && <p>No friends found.</p>}

        {/* Array.map() transforms each Friend object into a <li> element.
            The key prop must be unique and stable — React uses it to identify
            which DOM node to update / reuse during reconciliation.
            Using friend.id (a stable DB primary key) is the correct choice;
            using the array index would cause bugs when items are deleted. */}
        <ul>
          {friends.map((friend) => (
            <li key={friend.id}>
              <div>
                <strong>{friend.name}</strong>
                <span>{friend.email}</span>
                {/* Nullish fallback: show "No phone" if phone is null/empty. */}
                <span>{friend.phone || "No phone"}</span>
              </div>
              <div className="actions">
                {/* Arrow functions in onClick capture friend from the closure. */}
                <button onClick={() => startEdit(friend)}>Edit</button>
                <button onClick={() => removeFriend(friend.id)}>Delete</button>
              </div>
            </li>
          ))}
        </ul>

        {/* Pagination: only rendered when there is more than one page.
            Short-circuit evaluation (&&) prevents rendering the div otherwise. */}
        {totalPages > 1 && (
          <div className="pagination">
            {/* disabled prevents React from firing onClick when on page 0. */}
            <button disabled={page === 0} onClick={() => goToPage(page - 1)}>
              ← Prev
            </button>
            {/* page is 0-based internally; display as 1-based for users. */}
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

// =============================================================================
// Bootstrap — mount the React application
// =============================================================================
//
// ReactDOM.createRoot(container) creates a React root bound to the given DOM node.
// .render(<App />) mounts the App component tree into that root.
//
// createRoot is the React 18 API (replaces the deprecated ReactDOM.render).
// It enables concurrent features (automatic batching, Suspense, Transitions).
//
// <App /> is JSX for React.createElement(App, null) — Babel compiles it.
// This is the single entry point; React manages everything inside #root from here.
// =============================================================================
ReactDOM.createRoot(document.getElementById("root")).render(<App />);
