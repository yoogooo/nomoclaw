import DOMPurify from "dompurify";
import katex from "katex";
import MarkdownIt from "markdown-it";
import texmath from "markdown-it-texmath";
import "katex/dist/katex.min.css";
import "markdown-it-texmath/css/texmath.css";

const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
  html: false
}).enable("table");

// Render the TeX delimiters commonly emitted by assistants. Keeping this in
// the shared renderer also fixes formulas in runtime details and previews.
markdown.use(texmath, {
  engine: katex,
  delimiters: ["dollars", "brackets"],
  katexOptions: {
    throwOnError: false
  }
});

const defaultLinkOpenRenderer = markdown.renderer.rules.link_open
  ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options));

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx];
  const targetIndex = token.attrIndex("target");
  if (targetIndex < 0) {
    token.attrPush(["target", "_blank"]);
  } else {
    token.attrs![targetIndex][1] = "_blank";
  }

  const relIndex = token.attrIndex("rel");
  if (relIndex < 0) {
    token.attrPush(["rel", "noopener noreferrer"]);
  } else {
    token.attrs![relIndex][1] = "noopener noreferrer";
  }
  return defaultLinkOpenRenderer(tokens, idx, options, env, self);
};

export function renderMarkdown(content: string) {
  return DOMPurify.sanitize(markdown.render(content || ""), {
    ADD_ATTR: ["target", "rel"]
  });
}
