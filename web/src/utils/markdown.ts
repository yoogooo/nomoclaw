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

function cjkStrongBoundaryPlugin(md: MarkdownIt) {
  const punctuationAtEnd = /\p{P}$/u;
  const cjkCharacter = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}\u3000-\u303f]/u;

  md.inline.ruler.before("emphasis", "cjk_strong_boundary", (state: any, silent: boolean) => {
    const start = state.pos;
    if (state.src.slice(start, start + 2) !== "**") {
      return false;
    }

    let end = start + 2;
    while ((end = state.src.indexOf("**", end)) !== -1) {
      const inner = state.src.slice(start + 2, end);
      const nextCharacter = state.src[end + 2] || "";
      const isEscaped = state.src[end - 1] === "\\";
      if (
        inner
        && !inner.includes("\n")
        && !isEscaped
        && punctuationAtEnd.test(inner)
        && cjkCharacter.test(nextCharacter)
      ) {
        if (silent) {
          state.pos = end + 2;
          return true;
        }

        const baseLevel = state.level;
        const open = state.push("strong_open", "strong", 1);
        open.markup = "**";

        const children: any[] = [];
        state.md.inline.parse(inner, state.md, state.env, children);
        for (const child of children) {
          child.level += baseLevel + 1;
          state.tokens.push(child);
          state.tokens_meta.push(null);
        }

        const close = state.push("strong_close", "strong", -1);
        close.markup = "**";
        state.pos = end + 2;
        return true;
      }
      end += 2;
    }

    return false;
  });
}

markdown.use(cjkStrongBoundaryPlugin);

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
