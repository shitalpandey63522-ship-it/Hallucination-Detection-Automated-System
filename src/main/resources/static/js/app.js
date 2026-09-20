pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.4.120/pdf.worker.min.js';
  

    // Presets definitions
    const presets = {
      'ai-fake': {
        essay: `Quantum mechanics was revolutionized in 2026. A breakthrough study by Hawking (2026) titled "The Quantum Horizon of Black Hole Singularities" published in the Journal of Theoretical Einstein Physics, Volume 4, established a direct correlation between loop gravity and standard Hawking radiation. This theory completely contradicts earlier proofs that black holes disappear slowly. Furthermore, Albert Einstein released a paper in the Science Journal (2024) validating loops. In the paper, "Relativistic Singularities Refined" (Einstein, 2024), he presents calculations resolving dark matter.`,
        context: ""
      },
      'science-real': {
        essay: `Albert Einstein published the Special Theory of Relativity in 1905, establishing that the speed of light is constant in a vacuum. His General Theory of Relativity, published in 1916, explained gravity as the curvature of spacetime. These discoveries fundamentally changed the landscape of physics.`,
        context: "Albert Einstein published the Special Theory of Relativity in 1905 and the General Theory of Relativity in 1916. Relativity explains gravity as the curvature of space-time."
      },
      'contradict': {
        essay: `The Declaration of Independence of the United States was signed in 1985. It established complete independence from Spain. George Washington was famously Spain's chief delegate.`,
        context: "The United States Declaration of Independence was adopted on July 4, 1776, declaring independence from Great Britain. George Washington was the commander-in-chief of the Continental Army and later became the first US president."
      }
    };

    let activeData = null; // Currently loaded audit data
    let currentCiteSource = null; // Currently selected source for formatting

    // Counters listener
    const essayInput = document.getElementById('essay-input');
    essayInput.addEventListener('input', () => {
      updateCounters();
      document.getElementById('results-panel').style.display = 'none';
      document.getElementById('evidence-display-card').style.display = 'none';
    });

    document.getElementById('context-input').addEventListener('input', () => {
      document.getElementById('results-panel').style.display = 'none';
      document.getElementById('evidence-display-card').style.display = 'none';
    });

    function updateCounters() {
      const text = essayInput.value.trim();
      const chars = text.length;
      const words = text === "" ? 0 : text.split(/\s+/).length;
      document.getElementById('char-counter').textContent = `${chars} characters | ${words} words`;
    }

    function loadPreset(key) {
      if (presets[key]) {
        essayInput.value = presets[key].essay;
        document.getElementById('context-input').value = presets[key].context;
        updateCounters();
        const instruction = document.getElementById('upload-instruction');
        if (instruction) {
          instruction.innerHTML = `📄 Drag & drop essay, docx, pdf or <strong>browse your computer</strong> to upload`;
        }
        showToast("Preset Loaded");
      }
    }

    function clearWorkspace() {
      essayInput.value = "";
      document.getElementById('context-input').value = "";
      updateCounters();
      const instruction = document.getElementById('upload-instruction');
      if (instruction) {
        instruction.innerHTML = `📄 Drag & drop essay, docx, pdf or <strong>browse your computer</strong> to upload`;
      }
      document.getElementById('results-panel').style.display = 'none';
      document.getElementById('evidence-display-card').style.display = 'none';
    }

    function triggerFileInput() {
      document.getElementById('file-input').click();
    }

    function handleFileSelected(event) {
      const file = event.target.files[0];
      if (file) {
        handleFile(file);
      }
    }

    function handleFile(file) {
      const extension = file.name.split('.').pop().toLowerCase();
      
      const instruction = document.getElementById('upload-instruction');
      if (instruction) {
        instruction.innerHTML = `📄 Loaded File: <strong style="color:#a855f7">${file.name}</strong> (Click to change)`;
      }
      
      if (extension === 'docx') {
        const reader = new FileReader();
        reader.onload = function(e) {
          mammoth.extractRawText({ arrayBuffer: e.target.result })
            .then(function(result) {
              essayInput.value = result.value;
              updateCounters();
              showToast(`Docx "${file.name}" parsed successfully`);
            })
            .catch(function(err) {
              showToast("Error parsing Word document: " + err.message, true);
            });
        };
        reader.readAsArrayBuffer(file);
      } 
      else if (extension === 'pdf') {
        const reader = new FileReader();
        reader.onload = function(e) {
          const typedarray = new Uint8Array(e.target.result);
          pdfjsLib.getDocument(typedarray).promise.then(function(pdf) {
            let maxPages = pdf.numPages;
            let countPromises = [];
            for (let j = 1; j <= maxPages; j++) {
              countPromises.push(
                pdf.getPage(j).then(function(page) {
                  return page.getTextContent().then(function(textContent) {
                    return textContent.items.map(s => s.str).join(' ');
                  });
                })
              );
            }
            Promise.all(countPromises).then(function(pageTexts) {
              const parsedPdfText = pageTexts.join('\n\n');
              essayInput.value = parsedPdfText;
              updateCounters();
              showToast(`PDF "${file.name}" parsed successfully`);
            }).catch(function(err) {
              showToast("Error loading pages in PDF: " + err.message, true);
            });
          }).catch(function(err) {
            showToast("Failed to parse PDF document: " + err.message, true);
          });
        };
        reader.readAsArrayBuffer(file);
      } 
      else {
        const reader = new FileReader();
        reader.onload = function(e) {
          essayInput.value = e.target.result;
          updateCounters();
          showToast(`File "${file.name}" loaded successfully`);
        };
        reader.readAsText(file);
      }
    }

    // Bind Drag & Drop Events
    document.addEventListener('DOMContentLoaded', () => {
      const dragArea = document.querySelector('.upload-drag-area');
      if (dragArea) {
        dragArea.addEventListener('dragover', (e) => {
          e.preventDefault();
          dragArea.style.borderColor = '#ec4899';
          dragArea.style.background = 'rgba(168, 85, 247, 0.08)';
        });

        dragArea.addEventListener('dragleave', () => {
          dragArea.style.borderColor = 'rgba(168, 85, 247, 0.25)';
          dragArea.style.background = 'rgba(168, 85, 247, 0.02)';
        });

        dragArea.addEventListener('drop', (e) => {
          e.preventDefault();
          dragArea.style.borderColor = 'rgba(168, 85, 247, 0.25)';
          dragArea.style.background = 'rgba(168, 85, 247, 0.02)';
          const file = e.dataTransfer.files[0];
          if (file) {
            handleFile(file);
          }
        });
      }
    });

    async function handleRagFileSelected(event) {
      const file = event.target.files[0];
      if (!file) return;
      
      const fileName = file.name;
      const topic = document.getElementById('rag-topic-input').value.trim() || 'General';
      const docId = document.getElementById('rag-docid-input').value.trim() || fileName.split('.')[0].replaceAll(/[^a-zA-Z0-9-]/g, '-');

      showToast(`Uploading & Parsing ${fileName}...`, false);

      const formData = new FormData();
      formData.append('file', file);
      formData.append('topic', topic);
      formData.append('docId', docId);

      try {
        const resp = await fetch('/api/rag/upload', {
          method: 'POST',
          body: formData
        });
        const data = await resp.json();
        if (resp.ok && data.status === 'success') {
          showToast(data.message || `File ${fileName} vectorized cleanly into [${topic}] vector store!`, false);
          document.getElementById('rag-ingest-input').value = `[File embedded successfully: ${fileName}]`;
          updateQaTopicDropdown();
        } else {
          showToast(data.message || `Failed to process ${fileName}`, true);
        }
      } catch (err) {
        showToast(`Failed to upload ${fileName}`, true);
      }
    }

    function toggleRagPanel() {
      const body = document.getElementById('rag-panel-body');
      body.style.display = body.style.display === 'none' ? 'block' : 'none';
    }

    async function ingestRagDocument() {
      const text = document.getElementById('rag-ingest-input').value.trim();
      const topic = document.getElementById('rag-topic-input').value.trim() || 'General';
      const docId = document.getElementById('rag-docid-input').value.trim() || 'user-doc';
      if (!text) {
        showToast('Please enter reference text to ingest', true);
        return;
      }
      try {
        const resp = await fetch(`/api/rag/ingest?topic=${encodeURIComponent(topic)}&docId=${encodeURIComponent(docId)}`, {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain' },
          body: text
        });
        const data = await resp.json();
        showToast(data.message || `Ingested into topic [${topic}] vector store successfully!`, false);
        document.getElementById('rag-ingest-input').value = '';
      } catch (err) {
        showToast('Failed to ingest document into local vector store', true);
      }
    }

    async function clearRagDatabase() {
      const topic = document.getElementById('rag-topic-input').value.trim();
      const confirmMsg = topic 
        ? `Are you sure you want to clear all vector documents for topic [${topic}]?` 
        : `Are you sure you want to clear ALL vector databases across all topics?`;
        
      if (!confirm(confirmMsg)) return;

      try {
        const url = `/api/rag/clear${topic ? '?topic=' + encodeURIComponent(topic) : ''}`;
        const resp = await fetch(url, { method: 'DELETE' });
        const data = await resp.json();
        showToast(data.message || 'Vector database cleared successfully!', false);
        document.getElementById('rag-ingest-input').value = '';
      } catch (err) {
        showToast('Failed to clear vector database', true);
      }
    }

    async function updateQaTopicDropdown() {
      const select = document.getElementById('qa-topic-input');
      const currentValue = select.value;
      try {
        const res = await fetch('/api/rag/documents');
        if (!res.ok) return;
        const docs = await res.json();
        const topics = new Set(['General']);
        docs.forEach(d => { if (d.topic) topics.add(d.topic); });

        select.innerHTML = '<option value="">All Topics (Search Everything)</option>';
        topics.forEach(t => {
          const opt = document.createElement('option');
          opt.value = t;
          opt.textContent = `📁 Topic: ${t}`;
          select.appendChild(opt);
        });
        if (currentValue) select.value = currentValue;
      } catch (err) {}
    }

    function switchAppMode(mode) {
      const isAudit = mode === 'audit';
      document.getElementById('audit-input-panel').style.display = isAudit ? 'block' : 'none';
      document.getElementById('qa-mode-panel').style.display = isAudit ? 'none' : 'block';
      
      document.getElementById('mode-audit-btn').style.background = isAudit ? 'var(--accent)' : 'transparent';
      document.getElementById('mode-audit-btn').style.color = isAudit ? 'white' : 'var(--text-muted)';
      
      document.getElementById('mode-qa-btn').style.background = isAudit ? 'transparent' : 'var(--accent)';
      document.getElementById('mode-qa-btn').style.color = isAudit ? 'var(--text-muted)' : 'white';

      document.getElementById('workspace-title').textContent = isAudit ? 'Hallucination Detector' : 'Zero-Hallucination AI Assistant';
      document.getElementById('workspace-subtitle').textContent = isAudit 
        ? 'Paste text below to detect hallucinations, verify citations, and find credible academic references.'
        : 'Ask questions grounded strictly in local RAG vectors & Wikipedia. Zero guessing, zero hallucinations.';

      if (!isAudit) {
        updateQaTopicDropdown();
      }
    }

    async function runZeroHallucinationQA() {
      const q = document.getElementById('qa-question-input').value.trim();
      const topic = document.getElementById('qa-topic-input').value.trim();
      if (!q) {
        showToast('Please enter a question', true);
        return;
      }
      const btn = document.getElementById('qa-submit-btn');
      const box = document.getElementById('qa-result-box');
      const badge = document.getElementById('qa-status-badge');
      const txt = document.getElementById('qa-answer-text');
      const source = document.getElementById('qa-source-text');
      const limitations = document.getElementById('qa-limitations-text');

      try {
        btn.disabled = true;
        btn.style.opacity = '0.6';
        btn.style.cursor = 'not-allowed';
        btn.innerHTML = '⏳ Searching Vector RAG & Wikipedia...';

        box.style.display = 'block';
        box.style.borderColor = 'rgba(168, 85, 247, 0.4)';
        box.style.background = 'rgba(168, 85, 247, 0.05)';
        badge.textContent = '🔍 SEARCHING KNOWLEDGE BASE...';
        badge.style.color = '#c084fc';
        txt.textContent = 'Searching Local Vector RAG & live Wikipedia APIs... Please wait...';

        showToast('Querying Local Vector RAG & Wikipedia...', false);
        const url = `/api/audit/qa${topic ? '?topic=' + encodeURIComponent(topic) : ''}`;
        const resp = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain' },
          body: q
        });
        const data = await resp.json();

        if (data.verified) {
          box.style.borderColor = '#10b981';
          box.style.background = 'rgba(16, 185, 129, 0.05)';
          badge.textContent = '✅ VERIFIED GROUND-TRUTH ANSWER (0% HALLUCINATION)';
          badge.style.color = '#10b981';
        } else {
          box.style.borderColor = '#ef4444';
          box.style.background = 'rgba(239, 68, 68, 0.05)';
          badge.textContent = '⚠️ ZERO-HALLUCINATION GUARDRAIL ENGAGED (REFUSED TO GUESS)';
          badge.style.color = '#ef4444';
        }
        txt.textContent = data.answer;
        source.textContent = data.source ? `Source: ${data.source}` : '';
        limitations.textContent = data.verified
          ? 'Note: “Verified” means reference material was found; it is not a guarantee of zero hallucinations.'
          : 'No usable reference source was found, so the system refused to guess.';
      } catch (err) {
        showToast('Error generating zero-hallucination answer', true);
      } finally {
        btn.disabled = false;
        btn.style.opacity = '1';
        btn.style.cursor = 'pointer';
        btn.innerHTML = '🤖 Generate Verified Answer';
      }
    }

    function showToast(message, isError = false) {
      const toast = document.getElementById('toast-notif');
      toast.textContent = message;
      toast.style.background = isError ? '#ef4444' : '#10b981';
      toast.classList.add('show');
      setTimeout(() => {
        toast.classList.remove('show');
      }, 3000);
    }

    function switchTab(tab) {
      document.getElementById('tab-claims-btn').classList.toggle('active', tab === 'claims');
      document.getElementById('tab-contra-btn').classList.toggle('active', tab === 'contra');
      document.getElementById('tab-bib-btn').classList.toggle('active', tab === 'bib');
      
      document.getElementById('tab-claims').classList.toggle('active', tab === 'claims');
      document.getElementById('tab-contra').classList.toggle('active', tab === 'contra');
      document.getElementById('tab-bib').classList.toggle('active', tab === 'bib');
    }

    // Call Backend API
    async function runDetector() {
      const text = essayInput.value.trim();
      const context = document.getElementById('context-input').value.trim();
      
      if (text.length < 20) {
        showToast("Please enter at least 20 characters of text to scan.", true);
        return;
      }

      const detectBtn = document.getElementById('detect-btn');
      detectBtn.disabled = true;
      detectBtn.innerHTML = `Scanning... <span style="display:inline-block; animation:spin 1s linear infinite">⏳</span>`;

      const sourceMode = document.getElementById('context-source-mode').value;

      try {
        const res = await fetch('/api/audit', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            generatedText: text,
            groundTruthContext: context || null,
            contextSourceMode: sourceMode
          })
        });

        if (!res.ok) {
          throw new Error('API server returned error');
        }

        const data = await res.json();
        const filteredData = applyDomainFilter(data);
        processResults(filteredData);
        showToast("Document Analysis Completed");
      } catch (error) {
        console.error(error);
        showToast("Backend unavailable, executing offline heuristics", false);
        const offlineResult = runOfflineHeuristics(text, context);
        const filteredOffline = applyDomainFilter(offlineResult);
        processResults(filteredOffline);
      } finally {
        detectBtn.disabled = false;
        detectBtn.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          Scan Document
        `;
      }
    }

    // Offline parsing when server is unavailable
    function runOfflineHeuristics(text, context) {
      // Split text into basic sentences
      const sentences = text.match(/[^.!?]+[.!?]+/g) || [text];
      
      const evaluations = [];
      let contradictions = 0;
      let resolvedContext = context || "";

      sentences.forEach(s => {
        const sentence = s.trim();
        if (sentence.length < 5) return;
        
        let label = "NEUTRAL";
        let reasoning = "Offline check: Reference context is required to verify claims. Please provide a Ground Truth Context.";
        let evidence = [];

        const lowerS = sentence.toLowerCase();
        const lowerC = resolvedContext ? resolvedContext.toLowerCase() : "";

        if (resolvedContext) {
          // Compare against resolvedContext using generic logic
          const isContradiction = jsLooksLikeContradiction(sentence, resolvedContext);
          if (isContradiction) {
            label = "CONTRADICTION";
            reasoning = "Offline check: The claim contradicts facts in the reference context.";
            evidence = [{title: "Reference Context Contradiction", url: "#", summary: "Context does not match claim details."}];
          } else if (jsShareWords(lowerS, lowerC)) {
            label = "ENTAILMENT";
            reasoning = "Offline check: The claim is supported by facts in the reference context.";
            evidence = [{title: "Provided Reference Document", url: "#", summary: "Context matches claim keywords."}];
          } else {
            label = "NEUTRAL";
            reasoning = "Offline check: The reference context does not contain sufficient details to verify this claim.";
          }
        }

        if (label === "CONTRADICTION") contradictions++;
        
        evaluations.push({
          claim: sentence,
          label,
          reasoning,
          evidence
        });
      });

      // Extract mock citations
      const citations = [];
      const parentheticalRegex = /\(([^)]+)\)/g;
      let match;
      while ((match = parentheticalRegex.exec(text)) !== null) {
        const citeContent = match[1];
        if (citeContent.match(/[a-zA-Z]+,\s*\d{4}/)) {
          let status = "UNVERIFIED";
          let reasoning = "Offline check: Citation conforms to format but requires database verification.";
          let alt = null;
          
          const parseMatch = citeContent.match(/\b([A-Z][a-zA-Z]+)\b.*?\b(\d{4})\b/);
          if (parseMatch) {
            const author = parseMatch[1];
            const year = parseInt(parseMatch[2], 10);
            if (year > 2026) {
              status = "HALLUCINATED";
              reasoning = `Citation year ${year} is in the future. The author could not have published this work yet.`;
            }
          }

          citations.push({
            citationText: match[0],
            referenceDetail: citeContent + " paper reference.",
            status,
            reasoning,
            suggestedAlternative: alt
          });
        }
      }

      // Compute score
      let score = 0;
      if (evaluations.length > 0) {
        const neutrals = evaluations.filter(e => e.label === 'NEUTRAL').length;
        const contras = evaluations.filter(e => e.label === 'CONTRADICTION').length;
        const fakeCites = citations.filter(c => c.status === 'HALLUCINATED').length;
        
        const claimRisk = ((contras + (neutrals * 0.2)) / evaluations.length) * 100;
        const citationRisk = citations.length === 0 ? 0 : (fakeCites / citations.length) * 50;
        score = Math.min(100, Math.round(Math.max(claimRisk, citationRisk)));
      }
      
      const status = (score >= 30 || contradictions > 0 || citations.some(c => c.status === 'HALLUCINATED')) ? "FLAGGED" : "SAFE";

      return {
        hallucinationScore: score,
        status,
        reasoning: `Offline evaluation completed. Score calculated locally at ${score}%.`,
        detectedContradictions: evaluations.filter(e => e.label === "CONTRADICTION").map(e => ({
          claim: e.claim,
          label: e.label,
          reasoning: e.reasoning,
          evidence: e.evidence
        })),
        evaluations,
        checkedCitations: citations
      };
    }

    function applyDomainFilter(data) {
      const mode = document.getElementById('domain-mode').value;
      if (mode !== 'creative') {
        return data; // Strict mode, return unmodified
      }
      
      const creativeKeywords = ["imagine", "story", "fiction", "creative", "think", "once upon", "maybe", "opinion", "legend", "myth", "feel", "dream"];
      
      // Update evaluations
      let contras = 0;
      let neutrals = 0;
      let entailments = 0;
      
      const newEvaluations = (data.evaluations || []).map(e => {
        const text = e.claim || e.generatedText || "";
        const isCreative = creativeKeywords.some(kw => text.toLowerCase().includes(kw));
        if (isCreative && e.label !== 'ENTAILMENT') {
          return {
            ...e,
            label: 'ENTAILMENT',
            reasoning: 'Classified as creative narrative; bypassed factual verification checks.'
          };
        }
        return e;
      });
      
      newEvaluations.forEach(e => {
        if (e.label === 'ENTAILMENT') entailments++;
        else if (e.label === 'NEUTRAL') neutrals++;
        else if (e.label === 'CONTRADICTION') contras++;
      });
      
      // Recalculate score
      const total = newEvaluations.length;
      const fakeCites = (data.checkedCitations || []).filter(c => c.status === 'HALLUCINATED').length;
      
      const claimRisk = total === 0 ? 0 : ((contras + (neutrals * 0.2)) / total) * 100;
      const citationRisk = (data.checkedCitations || []).length === 0
        ? 0
        : (fakeCites / data.checkedCitations.length) * 50;
      const newScore = Math.min(100, Math.round(Math.max(claimRisk, citationRisk)));
      const newStatus = (newScore >= 30 || contras > 0 || fakeCites > 0) ? "FLAGGED" : "SAFE";
      
      return {
        ...data,
        hallucinationScore: newScore,
        status: newStatus,
        reasoning: `Audit sensitivity mode set to Creative. Score adjusted to ${newScore}%.`,
        detectedContradictions: newEvaluations.filter(e => e.label === "CONTRADICTION").map(e => ({
          claim: e.claim,
          label: e.label,
          reasoning: e.reasoning,
          evidence: e.evidence
        })),
        evaluations: newEvaluations
      };
    }

    // Helper functions for offline JS checking
    function jsLooksLikeContradiction(claim, ctx) {
      const c = claim.toLowerCase();
      const cx = ctx.toLowerCase();
      
      // 1. Negation conflict
      const negationWords = ["not", "never", "no", "denied", "refused", "incorrect", "false", "fail"];
      const claimHasNegation = negationWords.some(nw => new RegExp('\\b' + nw + '\\b').test(c));
      const ctxHasNegation = negationWords.some(nw => new RegExp('\\b' + nw + '\\b').test(cx));
      if (claimHasNegation !== ctxHasNegation && jsShareWords(c, cx)) {
        return true;
      }
      
      // 2. Numeric mismatch
      const yearRegex = /\b(\d{4}|\d+-%|\d+%)\b/g;
      let match;
      while ((match = yearRegex.exec(c)) !== null) {
        const num = match[1];
        if (!cx.includes(num)) {
          let ctxMatch;
          const ctxYearRegex = /\b(\d{4}|\d+-%|\d+%)\b/g;
          while ((ctxMatch = ctxYearRegex.exec(cx)) !== null) {
            const ctxNum = ctxMatch[1];
            if (isSameNumericType(num, ctxNum)) {
              return true;
            }
          }
        }
      }
      
      // 3. Proper noun mismatch
      if (jsShareWords(c, cx)) {
        const properNounRegex = /\b[A-Z][a-z]+\b/g;
        let pnMatch;
        const claimNouns = [];
        while ((pnMatch = properNounRegex.exec(claim)) !== null) {
          const word = pnMatch[0];
          if (!isStopWord(word.toLowerCase())) {
            claimNouns.push(word);
          }
        }
        if (claimNouns.length > 0) {
          const missingNoun = claimNouns.some(n => !cx.includes(n.toLowerCase()));
          if (missingNoun) {
            let ctxPnMatch;
            let ctxNounCount = 0;
            const ctxPnRegex = /\b[A-Z][a-z]+\b/g;
            while ((ctxPnMatch = ctxPnRegex.exec(ctx)) !== null) {
              if (!isStopWord(ctxPnMatch[0].toLowerCase())) {
                ctxNounCount++;
              }
            }
            if (ctxNounCount > 0) {
              return true;
            }
          }
        }
      }
      
      return false;
    }

    function isSameNumericType(n1, n2) {
      if (n1.length === 4 && n2.length === 4) return true;
      if (n1.endsWith("%") && n2.endsWith("%")) return true;
      if (n1.includes("-") && n2.includes("-")) return true;
      return false;
    }

    function jsShareWords(c, cx) {
      const stopWords = ["a", "an", "and", "are", "as", "at", "be", "been", "by", "for", "from", "he", "her", "his", "in", "is", "it", "of", "on", "or", "that", "the", "to", "was", "with"];
      const cWords = c.split(/\s+/).map(w => w.replace(/[^a-z0-9]/g, "")).filter(w => w.length > 2 && !stopWords.includes(w));
      const cxWords = cx.split(/\s+/).map(w => w.replace(/[^a-z0-9]/g, "")).filter(w => w.length > 2 && !stopWords.includes(w));
      if (cWords.length === 0) return false;
      
      const cxSet = new Set(cxWords);
      const matches = cWords.filter(w => cxSet.has(w));
      return (matches.length / cWords.length) >= 0.15;
    }

    function isStopWord(token) {
      const stopWords = [
        "a", "an", "and", "are", "as", "at", "be", "been", "being", "by", "for", "from",
        "had", "has", "have", "he", "her", "his", "i", "if", "in", "into", "is", "it", "its",
        "me", "my", "no", "not", "of", "on", "or", "our", "she", "that", "the", "their", "them",
        "they", "this", "to", "was", "we", "were", "what", "when", "where", "which", "who",
        "why", "will", "with", "you", "your"
      ];
      return stopWords.includes(token);
    }

    // Process & render results in dashboard
    function processResults(data) {
      activeData = data;
      document.getElementById('results-panel').style.display = 'flex';
      
      // Update score gauge
      const score = Math.round(data.hallucinationScore);
      document.getElementById('score-val').textContent = `${score}%`;
      
      // Arc calculation (r = 65, circumference = 408.4)
      const offset = 408.4 - (score / 100) * 408.4;
      const progressCircle = document.getElementById('score-progress');
      progressCircle.style.strokeDashoffset = offset;
      
      // Set color depending on score
      if (score < 20 && data.status !== 'FLAGGED') {
        progressCircle.style.stroke = 'var(--safe)';
        document.getElementById('report-verdict').textContent = 'Safe';
        document.getElementById('report-verdict').className = 'verdict-badge safe';
      } else if (score < 50 && data.status !== 'FLAGGED') {
        progressCircle.style.stroke = 'var(--warn)';
        document.getElementById('report-verdict').textContent = 'Caution';
        document.getElementById('report-verdict').className = 'verdict-badge warning';
        document.getElementById('report-verdict').style.color = 'var(--warn)';
        document.getElementById('report-verdict').style.borderColor = 'var(--warn-border)';
        document.getElementById('report-verdict').style.background = 'var(--warn-bg)';
      } else {
        progressCircle.style.stroke = 'var(--danger)';
        document.getElementById('report-verdict').textContent = 'Flagged';
        document.getElementById('report-verdict').className = 'verdict-badge flagged';
      }

      document.getElementById('report-description').textContent = data.reasoning;

      // Stats
      const totalClaims = data.evaluations ? data.evaluations.length : 0;
      const fakeCitations = data.checkedCitations ? data.checkedCitations.filter(c => c.status === 'HALLUCINATED').length : 0;
      
      let supportedClaims = 0;
      if (data.evaluations) {
        supportedClaims = data.evaluations.filter(e => e.label === 'ENTAILMENT').length;
      }
      const supportedRate = totalClaims === 0 ? 100 : Math.round((supportedClaims / totalClaims) * 100);
      
      let sourcesCount = 0;
      if (data.evaluations) {
        data.evaluations.forEach(e => {
          if (e.evidence && e.evidence.length > 0) sourcesCount += e.evidence.length;
        });
      }

      document.getElementById('stat-total-claims').textContent = totalClaims;
      document.getElementById('stat-fake-citations').textContent = fakeCitations;
      document.getElementById('stat-supported-rate').textContent = `${supportedRate}%`;
      document.getElementById('stat-sources-count').textContent = sourcesCount;

      // Highlight texts rendering
      renderHighlightedText();
      
      // Render contradictions tab
      renderContradictionsTable();

      // Render citations table
      renderCitationsTable();

      // Reset card display
      document.getElementById('evidence-display-card').style.display = 'none';

      // Refresh history list
      loadHistoryList();
    }

    function renderHighlightedText() {
      const viewer = document.getElementById('highlighted-text-viewer');
      viewer.innerHTML = "";

      if (!activeData || !activeData.evaluations || activeData.evaluations.length === 0) {
        viewer.textContent = essayInput.value;
        return;
      }

      activeData.evaluations.forEach((evalItem, index) => {
        const span = document.createElement('span');
        span.className = `claim-hl ${evalItem.label.toLowerCase()}`;
        span.textContent = evalItem.claim + " ";
        span.dataset.index = index;
        span.onclick = () => selectClaim(index);
        viewer.appendChild(span);
      });
    }

    function selectClaim(index) {
      // Highlight selection on screen
      document.querySelectorAll('.claim-hl').forEach(span => {
        span.classList.remove('selected');
      });
      document.querySelector(`.claim-hl[data-index="${index}"]`).classList.add('selected');

      const evalItem = activeData.evaluations[index];
      const card = document.getElementById('evidence-display-card');
      card.style.display = 'block';

      document.getElementById('evidence-claim-text').textContent = `"${evalItem.claim}"`;
      document.getElementById('evidence-verdict').textContent = evalItem.label;
      
      const badge = document.getElementById('evidence-verdict');
      badge.className = 'evidence-label';
      if (evalItem.label === 'ENTAILMENT') {
        badge.style.color = 'var(--safe)';
      } else if (evalItem.label === 'NEUTRAL') {
        badge.style.color = 'var(--warn)';
      } else {
        badge.style.color = 'var(--danger)';
      }

      document.getElementById('evidence-reasoning-text').textContent = evalItem.reasoning;

      const snippetSection = document.getElementById('snippet-section');
      if (evalItem.contradictingEvidenceSnippet && evalItem.contradictingEvidenceSnippet.trim() !== '') {
        snippetSection.style.display = 'block';
        document.getElementById('evidence-snippet-text').textContent = `"${evalItem.contradictingEvidenceSnippet}"`;
      } else {
        snippetSection.style.display = 'none';
      }

      const sourcesList = document.getElementById('sources-list');
      sourcesList.innerHTML = "";

      if (evalItem.evidence && evalItem.evidence.length > 0) {
        document.getElementById('sources-section').style.display = 'block';
        document.getElementById('citation-formatter-box').style.display = 'block';
        
        evalItem.evidence.forEach((src, sIndex) => {
          const item = document.createElement('div');
          item.className = 'evidence-source-item';
          
          const a = document.createElement('a');
          a.href = src.url && src.url !== '#' ? src.url : 'https://en.wikipedia.org/wiki/Special:Search?search=' + encodeURIComponent(src.title);
          a.target = '_blank';
          a.innerHTML = `${src.title} <span>↗</span>`;
          
          const title = document.createElement('h5');
          title.appendChild(a);
          item.appendChild(title);

          const p = document.createElement('p');
          p.textContent = src.summary || "Source reference matches claim details.";
          item.appendChild(p);

          item.onclick = () => {
            currentCiteSource = src;
            formatCite('apa');
          };

          sourcesList.appendChild(item);
        });

        // Auto select first source for formatting
        currentCiteSource = evalItem.evidence[0];
        formatCite('apa');
      } else {
        document.getElementById('sources-section').style.display = 'none';
        document.getElementById('citation-formatter-box').style.display = 'none';
      }
    }

    function formatCite(style) {
      document.querySelectorAll('.format-btn').forEach(btn => {
        btn.classList.toggle('active', btn.textContent.toLowerCase() === style);
      });

      if (!currentCiteSource) return;

      const text = document.getElementById('formatted-citation-text');
      const author = "Wikipedia Contributors";
      const year = new Date().getFullYear();
      const title = currentCiteSource.title;
      const url = currentCiteSource.url && currentCiteSource.url !== '#' ? currentCiteSource.url : "https://en.wikipedia.org/wiki/" + encodeURIComponent(title);

      if (style === 'apa') {
        text.textContent = `${author}. (${year}). ${title}. Retrieved from ${url}`;
      } else if (style === 'mla') {
        text.textContent = `"${title}." Wikipedia, The Free Encyclopedia, ${year}, ${url}.`;
      } else if (style === 'chicago') {
        text.textContent = `Wikipedia contributors, "${title}," Wikipedia, The Free Encyclopedia, ${url} (accessed ${new Date().toLocaleDateString()}).`;
      } else if (style === 'ieee') {
        text.textContent = `[1] "${title}," Wikipedia, ${year}. [Online]. Available: ${url}.`;
      }
    }

    function renderContradictionsTable() {
      const container = document.getElementById('contradictions-list-container');
      const countSpan = document.getElementById('contra-tab-count');
      container.innerHTML = "";

      // Extract contradictions either from detectedContradictions array or evaluations filtered by CONTRADICTION
      let contras = [];
      if (activeData && activeData.detectedContradictions && activeData.detectedContradictions.length > 0) {
        contras = activeData.detectedContradictions;
      } else if (activeData && activeData.evaluations) {
        contras = activeData.evaluations.filter(e => e.label === 'CONTRADICTION');
      }

      countSpan.textContent = contras.length;

      if (contras.length === 0) {
        container.innerHTML = `
          <div style="background: rgba(34, 197, 94, 0.05); border: 1px solid rgba(34, 197, 94, 0.2); border-radius: 12px; padding: 24px; text-align: center;">
            <div style="font-size: 24px; margin-bottom: 8px;">✅</div>
            <h4 style="color: var(--safe); font-size: 16px; font-weight: 600; margin-bottom: 4px;">Zero Contradictions Detected</h4>
            <p style="color: var(--muted); font-size: 13px; margin: 0;">No statements in the scanned text directly conflict with or contradict the ground truth reference context or Wikipedia facts.</p>
          </div>
        `;
        return;
      }

      contras.forEach((item, idx) => {
        const card = document.createElement('div');
        card.className = 'evidence-source-item';
        card.style.borderColor = 'rgba(239, 68, 68, 0.4)';
        card.style.background = 'rgba(239, 68, 68, 0.04)';
        card.style.padding = '18px';
        card.style.borderRadius = '12px';

        const claimText = item.claim || item.generatedText || "Scanned Statement";
        const reasoning = item.reasoning || "Direct contradiction identified between text and reference source.";
        
        let groundTruthFactsHtml = "";
        if (item.evidence && item.evidence.length > 0) {
          groundTruthFactsHtml = item.evidence.map(ev => `
            <div style="margin-top: 10px; font-size: 13px; background: rgba(34, 197, 94, 0.08); border: 1px solid rgba(34, 197, 94, 0.25); padding: 10px 14px; border-radius: 8px;">
              <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                <span style="font-size: 14px;">📌</span>
                <strong style="color: #4ade80; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Actual Ground Truth Fact (${ev.title || 'Reference Document'}):</strong>
              </div>
              <div style="color: #f0fdf4; line-height: 1.5; font-weight: 500;">${ev.summary || 'Ground truth reference matches contradiction point.'}</div>
            </div>
          `).join('');
        } else {
          groundTruthFactsHtml = `
            <div style="margin-top: 10px; font-size: 13px; background: rgba(59, 130, 246, 0.08); border: 1px solid rgba(59, 130, 246, 0.25); padding: 10px 14px; border-radius: 8px;">
              <strong style="color: #60a5fa; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">Actual Fact Reference:</strong>
              <div style="color: #eff6ff; line-height: 1.5; font-weight: 500;">${reasoning}</div>
            </div>
          `;
        }

        card.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <span style="background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px;">
              🚨 Contradiction #${idx + 1}
            </span>
            <span style="font-size: 11px; color: var(--muted);">Failed Verification</span>
          </div>

          <!-- Failed Scanned Input Claim -->
          <div style="margin-bottom: 10px; background: rgba(15, 23, 42, 0.6); padding: 10px 14px; border-radius: 8px; border-left: 4px solid var(--danger);">
            <strong style="font-size: 11px; color: #f87171; text-transform: uppercase; letter-spacing: 0.5px; display: block; margin-bottom: 4px;">❌ False / Contradictory Scanned Claim:</strong>
            <div style="font-size: 14px; color: white; line-height: 1.5; font-style: italic;">"${claimText}"</div>
          </div>

          <!-- Actual Fact Source -->
          ${groundTruthFactsHtml}

          <!-- Reason why it failed -->
          <div style="margin-top: 10px; padding-left: 4px;">
            <strong style="font-size: 11px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.5px; display: block; margin-bottom: 2px;">Why It Failed:</strong>
            <div style="font-size: 13px; color: #cbd5e1; line-height: 1.5;">${reasoning}</div>
          </div>
        `;

        container.appendChild(card);
      });
    }

    function renderCitationsTable() {
      const tbody = document.getElementById('bibliography-table-body');
      tbody.innerHTML = "";

      if (!activeData || !activeData.checkedCitations || activeData.checkedCitations.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--muted)">No citations or references were parsed from the input document.</td></tr>`;
        return;
      }

      activeData.checkedCitations.forEach(cit => {
        const tr = document.createElement('tr');

        // Column 1: Marker
        const tdMarker = document.createElement('td');
        tdMarker.style.fontWeight = 'bold';
        tdMarker.textContent = cit.citationText;
        tr.appendChild(tdMarker);

        // Column 2: Detail
        const tdDetail = document.createElement('td');
        tdDetail.textContent = cit.referenceDetail;
        tr.appendChild(tdDetail);

        // Column 3: Status
        const tdStatus = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = `bib-status-badge ${cit.status.toLowerCase()}`;
        
        let icon = "⚠️";
        if (cit.status === "VERIFIED") icon = "✅";
        if (cit.status === "UNVERIFIED") icon = "ℹ️";
        badge.textContent = `${icon} ${cit.status}`;
        
        tdStatus.appendChild(badge);
        tr.appendChild(tdStatus);

        // Column 4: Reasoning & Alternative
        const tdReason = document.createElement('td');
        tdReason.innerHTML = `<div>${cit.reasoning}</div>`;
        
        if (cit.suggestedAlternative) {
          const altBox = document.createElement('div');
          altBox.className = 'suggested-alternative-box';
          altBox.innerHTML = `<strong>💡 Suggested Real Publication:</strong> <div>${cit.suggestedAlternative}</div>`;
          tdReason.appendChild(altBox);
        }

        tr.appendChild(tdReason);
        tbody.appendChild(tr);
      });
    }

    // History logs management
    async function loadHistoryList() {
      try {
        const res = await fetch('/api/audit?limit=8');
        if (!res.ok) return;
        const list = await res.json();
        
        const container = document.getElementById('history-list');
        if (list.length === 0) {
          container.innerHTML = `<div style="font-size:12px; color:var(--muted)">No previous scans found.</div>`;
          return;
        }
        
        container.innerHTML = "";
        list.forEach(item => {
          const div = document.createElement('div');
          div.className = 'history-item';
          div.onclick = () => loadHistoryId(item.id);

          const date = new Date(item.createdAt).toLocaleDateString([], {month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'});
          const score = Math.round(item.hallucinationScore);
          
          let color = 'var(--safe)';
          if (score >= 50) color = 'var(--danger)';
          else if (score >= 20) color = 'var(--warn)';

          div.innerHTML = `
            <div>
              <div style="font-weight: 500; color:white;">Scan #${item.id}</div>
              <div style="font-size:11px; color:var(--muted); margin-top:2px;">${date}</div>
            </div>
            <span class="score-badge" style="background:rgba(255,255,255,0.05); color:${color}; border:1px solid ${color}33;">${score}%</span>
          `;
          container.appendChild(div);
        });
      } catch (err) {
        console.log("Could not load history list");
      }
    }

    async function loadHistoryId(id) {
      try {
        const res = await fetch(`/api/audit/${id}`);
        if (!res.ok) throw new Error();
        const data = await res.json();
        
        essayInput.value = data.generatedText;
        document.getElementById('context-input').value = data.groundTruthContext;
        updateCounters();
        
        // Re-construct matching schema structure
        const reMapped = {
          hallucinationScore: data.hallucinationScore,
          status: data.status,
          reasoning: data.reasoning,
          detectedContradictions: data.detectedContradictions,
          evaluations: data.evaluations || [],
          checkedCitations: data.checkedCitations || []
        };
        
        // If evaluations is empty (e.g. older formats), regenerate locally
        if (reMapped.evaluations.length === 0) {
          const mock = runOfflineHeuristics(data.generatedText, data.groundTruthContext);
          reMapped.evaluations = mock.evaluations;
          reMapped.checkedCitations = mock.checkedCitations;
        }

        processResults(reMapped);
        showToast(`Loaded Scan #${id} from history`);
      } catch (err) {
        showToast("Error loading scan from history", true);
      }
    }

    // Initial setups
    function escapeHtml(str) {
      if (!str) return '';
      return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

    // RAG Viewer Modal Logic
    let allIngestedRagDocs = [];

    async function openRagViewerModal() {
      document.getElementById('rag-viewer-modal').style.display = 'flex';
      await fetchAndRenderRagData();
    }

    function closeRagViewerModal() {
      document.getElementById('rag-viewer-modal').style.display = 'none';
    }

    async function fetchAndRenderRagData() {
      const container = document.getElementById('rag-modal-doc-list');
      container.innerHTML = '<div style="color:var(--muted); text-align:center; padding:30px;">Loading embedded vector store documents...</div>';
      try {
        const res = await fetch('/api/rag/documents');
        if (!res.ok) throw new Error('Failed to fetch documents');
        allIngestedRagDocs = await res.json();
        renderModalRagDocs(allIngestedRagDocs);
      } catch (err) {
        container.innerHTML = `<div style="color:#f87171; text-align:center; padding:30px;">Failed to load vector store documents: ${err.message}</div>`;
      }
    }

    function renderModalRagDocs(docs) {
      const container = document.getElementById('rag-modal-doc-list');
      const countSpan = document.getElementById('rag-modal-doc-count');
      countSpan.innerText = `${docs.length} Document(s) Embedded`;
      
      if (!docs || docs.length === 0) {
        container.innerHTML = `
          <div style="text-align:center; padding:40px 20px; color:var(--muted);">
            <div style="font-size:2.5rem; margin-bottom:12px;">📭</div>
            <p style="font-size:1rem; color:white; font-weight:600;">No Vector Documents Embedded</p>
            <p style="font-size:0.8rem; margin-top:6px; color:var(--muted);">Upload a PDF, DOCX, TXT, or MD file using the drag & drop zone to embed vector knowledge.</p>
          </div>`;
        return;
      }

      container.innerHTML = docs.map((doc, idx) => `
        <div style="background:rgba(255,255,255,0.03); border:1px solid rgba(168,85,247,0.25); border-radius:12px; padding:16px; box-shadow:0 4px 12px rgba(0,0,0,0.2);">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="background:rgba(168,85,247,0.2); color:#c084fc; border:1px solid rgba(168,85,247,0.4); font-size:0.7rem; font-weight:700; padding:3px 8px; border-radius:6px; text-transform:uppercase;">${escapeHtml(doc.topic || 'General')}</span>
              <span style="font-weight:700; color:white; font-size:0.95rem;">📄 ${escapeHtml(doc.docId || 'doc-' + (idx + 1))}</span>
            </div>
            <span style="font-size:0.75rem; color:#94a3b8; background:rgba(255,255,255,0.05); padding:2px 8px; border-radius:12px;">${doc.text ? doc.text.length : 0} characters</span>
          </div>
          <div style="background:rgba(0,0,0,0.4); border:1px solid rgba(255,255,255,0.06); border-radius:8px; padding:12px; font-family:monospace; font-size:0.82rem; color:#e2e8f0; max-height:140px; overflow-y:auto; white-space:pre-wrap; word-break:break-word;">${escapeHtml(doc.text)}</div>
        </div>
      `).join('');
    }

    function filterModalRagDocs() {
      const query = document.getElementById('rag-modal-search').value.toLowerCase().trim();
      if (!query) {
        renderModalRagDocs(allIngestedRagDocs);
        return;
      }
      const filtered = allIngestedRagDocs.filter(d => 
        (d.topic && d.topic.toLowerCase().includes(query)) ||
        (d.docId && d.docId.toLowerCase().includes(query)) ||
        (d.text && d.text.toLowerCase().includes(query))
      );
      renderModalRagDocs(filtered);
    }
