
const $ = (id) => document.getElementById(id);

let selectedBookId = null;
let toastTimer;

// Improved API handler: safely handles empty or invalid JSON responses.
async function api(url, options = {}) {
    let response;

    try {
        response = await fetch(url, options);
    } catch (error) {
        throw new Error(
            "Cannot connect to the server. Please check whether Java backend is running."
        );
    }

    const responseText = await response.text();
    let data = null;

    if (responseText.trim()) {
        try {
            data = JSON.parse(responseText);
        } catch (error) {
            throw new Error(
                "Server returned an invalid response. Check the Java backend."
            );
        }
    }

    if (!response.ok) {
        const message =
            data && typeof data.message === "string"
                ? data.message
                : `Request failed with HTTP ${response.status}.`;

        throw new Error(message);
    }

    if (!responseText.trim()) {
        // Some successful responses, such as HTTP 204, have no body.
        if (response.status === 204) {
            return null;
        }

        throw new Error(
            "The server returned an empty response. Check the Java backend."
        );
    }

    return data;
}

// Show notification.
function showToast(message, error = false) {
    const toast = $("toast");

    if (!toast) {
        console.log(message);
        return;
    }

    toast.textContent = message;
    toast.className = "toast show" + (error ? " error" : "");

    clearTimeout(toastTimer);

    toastTimer = setTimeout(() => {
        toast.className = "toast";
    }, 3500);
}

// Escape HTML to safely display book details.
function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}

// Load books from Java backend.
async function loadBooks() {
    try {
        const books = await api("/api/books");

        if (!Array.isArray(books)) {
            throw new Error(
                "Unexpected book catalogue response. Check the Java backend."
            );
        }

        $("totalBooks").textContent = books.length;
        $("bookCount").textContent = books.length;

        $("emptyState").hidden = books.length !== 0;

        $("bookGrid").innerHTML = books.map((book) => `
            <article class="book-card">
                <div class="book-cover" aria-hidden="true">📖</div>
                <span class="book-id">
                    BOOK ID · ${escapeHTML(book.id)}
                </span>
                <h3>${escapeHTML(book.title)}</h3>
                <p class="author">by ${escapeHTML(book.author)}</p>
                <div class="card-footer">
                    <span>Available in catalogue</span>
                    <button class="card-delete"
                        data-delete-id="${escapeHTML(book.id)}"
                        type="button">Remove</button>
                </div>
            </article>
        `).join("");

    } catch (error) {
        console.error("Load books error:", error);
        showToast(error.message, true);
    }
}

// Add a book.
$("bookForm").addEventListener("submit", async (event) => {
    event.preventDefault();

    const id = $("bookId").value.trim();
    const title = $("bookTitle").value.trim();
    const author = $("bookAuthor").value.trim();

    if (!id || !/^\d+$/.test(id) || Number(id) <= 0 ||
        !title || !author) {
        showToast("Please enter a valid Book ID, title and author.", true);
        return;
    }

    try {
        const body = new URLSearchParams({ id, title, author });

        const result = await api("/api/books", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: body.toString()
        });

        $("bookForm").reset();

        showToast(
            result?.message || "Book added successfully!"
        );

        await loadBooks();

    } catch (error) {
        console.error("Add book error:", error);
        showToast(error.message, true);
    }
});

// Search for a book.
$("searchForm").addEventListener("submit", async (event) => {
    event.preventDefault();

    const id = $("searchId").value.trim();

    if (!id) {
        showToast("Please enter a Book ID.", true);
        return;
    }

    selectedBookId = null;
    $("deleteButton").disabled = true;

    try {
        const book = await api(
            "/api/search?id=" + encodeURIComponent(id)
        );

        if (!book || book.id === undefined) {
            throw new Error("The server returned incomplete book details.");
        }

        selectedBookId = Number(book.id);
        $("deleteButton").disabled = false;

        $("searchResult").innerHTML = `
            <div class="result-card">
                <span class="book-id">
                    ✦ BOOK FOUND · ID ${escapeHTML(book.id)}
                </span>
                <h3>${escapeHTML(book.title)}</h3>
                <p>Written by ${escapeHTML(book.author)}</p>
            </div>
        `;

        showToast("Book found successfully!");

    } catch (error) {
        console.error("Search book error:", error);

        $("searchResult").innerHTML = `
            <div class="empty-symbol">⌕</div>
            <p>${escapeHTML(error.message)}</p>
        `;

        showToast(error.message, true);
    }
});

// Delete a book.
async function deleteBook(id) {
    if (!confirm(`Remove Book ID ${id} from the catalogue?`)) {
        return;
    }

    try {
        const result = await api(
            "/api/books?id=" + encodeURIComponent(id),
            { method: "DELETE" }
        );

        if (selectedBookId === Number(id)) {
            selectedBookId = null;
            $("deleteButton").disabled = true;

            $("searchResult").innerHTML = `
                <div class="empty-symbol">⌕</div>
                <p>Search for another book.</p>
            `;
        }

        showToast(
            result?.message || "Book removed successfully!"
        );

        await loadBooks();

    } catch (error) {
        console.error("Delete book error:", error);
        showToast(error.message, true);
    }
}

// Delete the book selected through Search.
$("deleteButton").addEventListener("click", () => {
    if (selectedBookId !== null) {
        deleteBook(selectedBookId);
    }
});

// Delete a book directly from its catalogue card.
$("bookGrid").addEventListener("click", (event) => {
    const button = event.target.closest("[data-delete-id]");

    if (button) {
        deleteBook(button.dataset.deleteId);
    }
});

// Refresh catalogue.
$("refreshButton").addEventListener("click", () => {
    loadBooks();
});


// Load the catalogue when the page opens.
loadBooks();

