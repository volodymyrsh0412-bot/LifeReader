// Viber: kozhne povidomlennia v chati - odna zupynka. Vbudovanyi skrypt Niky.
// Port skryptu "Viber: povidomlennia" (SandFly, avtor Kostya) na JavaScript.
export const manifest = {
  id: 'viber-messages',
  name: 'Viber: повідомлення',
  version: '1.0',
  author: 'Kostya (SandFly), Nika',
  description: 'У чатах Viber кожне повідомлення читається як одна зупинка: хто, кому відповідає, текст, цитата, реакції й час. Кнопки повідомлення — у меню дій з українськими назвами.',
  apps: ['com.viber.voip'],
  permissions: ['screen', 'actions', 'speech', 'input'],
  rules: [
    { match: { parentId: 'conversation_recycler_view' }, group: true, activate: { id: 'balloonView' } },
    { id: 'btn_jump_to_bottom', label: 'До останніх повідомлень' },
    { id: 'options_menu_open_more', label: 'Інші вкладення' },
  ],
};

const QUOTE_MAX = 120;

const KNOWN = {
  avatarView: true, balloonView: true, nameView: true, replyView: true, reactionView: true,
  forwardRootView: true, imageRemixView: true, timestampView: true, textMessageView: true,
  newMessageHeaderView: true, summarizeBannerStub: true,
};

function clean(text) {
  if (typeof text !== 'string') return null;
  const t = text.replace(/[‎‏‪-‮]/g, '').trim();
  return t === '' ? null : t;
}

function ownText(item) {
  return clean(item.text) || clean(item.contentDescription);
}

function shortId(item) {
  if (!item || typeof item.id !== 'string') return null;
  const m = item.id.match(/([^/]+)$/);
  return m ? m[1] : null;
}

function kidsOf(item) {
  if (!item || !item.childCount) return [];
  try {
    const list = typeof item.children === 'function' ? item.children() : item.children;
    return Array.isArray(list) ? list : [];
  } catch (e) {
    return [];
  }
}

function partsOf(row) {
  const kids = kidsOf(row);
  if (kids.length === 0) return null;
  const byId = {};
  const others = [];
  for (const kid of kids) {
    const id = shortId(kid);
    if (id && KNOWN[id]) {
      if (!byId[id]) byId[id] = kid;
    } else {
      others.push(kid);
    }
  }
  if (!(byId.balloonView || byId.textMessageView || byId.timestampView)) return null;
  return { byId, others };
}

function childText(item, id) {
  for (const kid of kidsOf(item)) {
    if (shortId(kid) === id) return ownText(kid);
  }
  return null;
}

function cut(text, max) {
  const chars = Array.from(text);
  if (chars.length <= max) return text;
  return chars.slice(0, max).join('').replace(/\s+\S*$/, '') + '…';
}

function reactionText(item) {
  const text = item && clean(item.contentDescription);
  if (!text) return null;
  const m = text.match(/(\d+)/);
  if (m) return parseInt(m[1], 10) > 0 ? 'реакцій: ' + m[1] : null;
  if (/[а-яіїєґА-ЯІЇЄҐ]/.test(text)) return text;
  return null;
}

function sentences(list) {
  let out = '';
  for (const part of list) {
    if (!part) continue;
    if (out === '') out = part;
    else if (/[.!?:…]$/.test(out)) out += ' ' + part;
    else out += '. ' + part;
  }
  return out;
}

function messageText(parts) {
  const byId = parts.byId;
  const said = [];
  if (byId.newMessageHeaderView) said.push(ownText(byId.newMessageHeaderView));
  const name = byId.nameView ? ownText(byId.nameView) : null;
  const author = childText(byId.replyView, 'replyAuthorView');
  const quote = childText(byId.replyView, 'replyQuoteView');

  const extra = [];
  for (const kid of parts.others) {
    if (kid.clickable !== true) {
      const t = ownText(kid);
      if (t) extra.push(t);
    }
  }
  let body = byId.textMessageView ? ownText(byId.textMessageView) : null;
  if (!body && byId.imageRemixView) body = 'Фото';
  if (!body && extra.length > 0) body = extra.shift();
  body = body || 'Повідомлення без тексту';

  if (name && author) said.push(name + ' відповідає ' + author + ': ' + body);
  else if (name) said.push(name + ': ' + body);
  else if (author) said.push('Відповідь для ' + author + ': ' + body);
  else said.push(body);
  if (quote) said.push((author ? 'На повідомлення: «' : 'У відповідь на «') + cut(quote, QUOTE_MAX) + '»');
  for (const t of extra) said.push(t);
  said.push(reactionText(byId.reactionView));
  said.push(byId.timestampView ? ownText(byId.timestampView) : null);
  return sentences(said);
}

backtalk.onFocus((item) => {
  try {
    const parts = partsOf(item);
    if (parts) return messageText(parts);
    if (/TextView$/.test(item.className || '') && /^\d+$/.test(clean(item.text) || '')) {
      const parent = typeof item.parent === 'function' ? item.parent() : null;
      if (parent && kidsOf(parent).some((k) => shortId(k) === 'btn_jump_to_bottom')) {
        return 'Нових повідомлень: ' + clean(item.text);
      }
    }
  } catch (e) {
    console.error('viber-messages: ' + e);
  }
  return undefined;
});

async function press(part, long) {
  if (long) {
    await part.longClick();
  } else {
    await part.click();
  }
}

backtalk.onActions((item) => {
  const parts = partsOf(item);
  if (!parts) return [];
  const byId = parts.byId;
  const list = [];
  const add = (title, part, long) => {
    if (part) list.push({ title: title, run: () => press(part, long) });
  };
  add('Меню повідомлення', byId.balloonView || item, true);
  const name = byId.nameView ? ownText(byId.nameView) : null;
  add(name ? 'Профіль: ' + name : 'Профіль', byId.avatarView || byId.nameView, false);
  const author = childText(byId.replyView, 'replyAuthorView');
  add(author ? 'Перейти до повідомлення: ' + author : 'Перейти до цитованого повідомлення', byId.replyView, false);
  let reaction = null;
  if (byId.reactionView) {
    try {
      reaction = byId.reactionView.find({ clickable: true });
    } catch (e) {
      reaction = null;
    }
  }
  add('Реакція', reaction, false);
  add('Переслати', byId.forwardRootView, false);
  add('Редагувати фото', byId.imageRemixView, false);
  if (byId.summarizeBannerStub) {
    const title = childText(byId.summarizeBannerStub, 'summarize_banner_counter_title');
    add('Стислий виклад' + (title ? ': ' + title : ''), byId.summarizeBannerStub, false);
  }
  for (const kid of parts.others) {
    const label = kid.clickable === true ? ownText(kid) : null;
    if (label) add(label, kid, false);
  }
  return list;
});
