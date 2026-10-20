// A source snapshot never initializes editor mutation handlers or autosave.
if(new URLSearchParams(location.search).get('view')==='source')await import('./source-view.js');
else await import('./code-workspace.js');
