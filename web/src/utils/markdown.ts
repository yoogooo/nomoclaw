import DOMPurify from "dompurify";
import katex from "katex";
import MarkdownIt from "markdown-it";
import texmath from "markdown-it-texmath";
import "katex/dist/katex.min.css";
import "markdown-it-texmath/css/texmath.css";

const cjkStrongBoundaryMarker = "mdCjkStrongBoundary9x";
const protectedMarkdownSegmentPattern = /(`{3,}[\s\S]*?`{3,}|~{3,}[\s\S]*?~{3,}|`[^`\n]*`|\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\]|(?<!\\)\$(?!\$)(?:\\.|[^$\\\n])+(?<!\\)\$)/g;

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

function normalizeCjkStrongBoundaries(content: string) {
  let normalized = "";
  let cursor = 0;

  for (const match of content.matchAll(protectedMarkdownSegmentPattern)) {
    const segmentStart = match.index ?? 0;
    normalized += normalizePlainMarkdownSegment(content.slice(cursor, segmentStart));
    normalized += match[0];
    cursor = segmentStart + match[0].length;
  }

  return normalized + normalizePlainMarkdownSegment(content.slice(cursor));
}

function normalizePlainMarkdownSegment(content: string) {
  // markdown-it does not recognize a closing strong delimiter when the
  // strong content ends in CJK punctuation such as "）" and is followed by
  // another CJK character. Add a temporary ASCII character inside the strong
  // span so its delimiter rules can match, then remove it after rendering.
  return content.replace(
    /\*\*([^*\n]*\p{P})\*\*(?=[\p{Script=Han}\u3000-\u303f])/gu,
    `**$1${cjkStrongBoundaryMarker}**`
  );
}

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
  const rendered = markdown.render(normalizeCjkStrongBoundaries(content || ""));
  return DOMPurify.sanitize(rendered.replaceAll(cjkStrongBoundaryMarker, ""), {
    ADD_ATTR: ["target", "rel"]
  });
}
