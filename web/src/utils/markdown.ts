import DOMPurify from "dompurify";
import MarkdownIt from "markdown-it";

const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
  html: false
}).enable("table");

export function renderMarkdown(content: string) {
  return DOMPurify.sanitize(markdown.render(content || ""));
}
