import java.util.ArrayList;
import java.util.List;

public class BST {
    private static class Node {
        Book book;
        Node left, right;

        Node(Book book) {
            this.book = book;
        }
    }

    private Node root;

    public boolean insert(Book book) {
        if (search(book.getBookId()) != null) {
            return false;
        }
        root = insertNode(root, book);
        return true;
    }

    private Node insertNode(Node node, Book book) {
        if (node == null) return new Node(book);

        if (book.getBookId() < node.book.getBookId()) {
            node.left = insertNode(node.left, book);
        } else {
            node.right = insertNode(node.right, book);
        }
        return node;
    }

    public Book search(int id) {
        Node current = root;

        while (current != null) {
            if (id == current.book.getBookId()) {
                return current.book;
            }

            current = id < current.book.getBookId()
                    ? current.left : current.right;
        }
        return null;
    }

    public boolean delete(int id) {
        if (search(id) == null) return false;
        root = deleteNode(root, id);
        return true;
    }

    private Node deleteNode(Node node, int id) {
        if (node == null) return null;

        if (id < node.book.getBookId()) {
            node.left = deleteNode(node.left, id);
        } else if (id > node.book.getBookId()) {
            node.right = deleteNode(node.right, id);
        } else {
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;

            Node successor = node.right;
            while (successor.left != null) {
                successor = successor.left;
            }

            node.book = successor.book;
            node.right = deleteNode(
                    node.right, successor.book.getBookId());
        }
        return node;
    }

    public List<Book> getSortedBooks() {
        List<Book> books = new ArrayList<>();
        inorder(root, books);
        return books;
    }

    private void inorder(Node node, List<Book> books) {
        if (node == null) return;

        inorder(node.left, books);
        books.add(node.book);
        inorder(node.right, books);
    }
}