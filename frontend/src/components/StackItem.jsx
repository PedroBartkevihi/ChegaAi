export default function StackItem({ title, body }) {
  return (
    <article className="stack-item">
      <strong>{title}</strong>
      <p>{body}</p>
    </article>
  );
}
