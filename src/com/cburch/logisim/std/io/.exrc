if &cp | set nocp | endif
let s:cpo_save=&cpo
set cpo&vim
map! <D-v> *
vnoremap  "*y
nmap [25~ "*
map Q <Nop>
nmap Z :redo " redo
nnoremap <silent> \ai :call AddMissingJavaImports()
nnoremap <silent> \fi :call AddMissingJavaImports()
nnoremap <silent> \si :call SortJavaImportGroupBelowCursor()
nnoremap <silent> \is :call SortJavaImportGroupBelowCursor()
xmap gx <Plug>(open-word-under-cursor)
nmap gx <Plug>(open-word-under-cursor)
map gQ gq " typo fix
xmap u <Nop>
xnoremap <Plug>(open-word-under-cursor) <ScriptCmd>vim9.Open(getregion(getpos('v'), getpos('.'), { type: mode() })->join())
nnoremap <Plug>(open-word-under-cursor) <ScriptCmd>vim9.Open(GetWordUnderCursor())
map <F10> :make
map <F8> :1,$diffput:q!:w:q " diff put, save other, and exit
map <S-F9> :res -5 " make window smaller
map <F9> :res +5 " make window larger
nmap <S-F5> :w:cn " save and go to next error
map <S-F4> :qall " quit all
nmap <F6> :w:make " save and make
nmap <F5> :cn " go to next error
nmap <F4> :cp " go to prev error
vnoremap <C-C> "*y
map <F1> "*y
nmap <S-F7> :w!:!aspell check %:e! % " spellcheck
vmap <BS> "-d
vmap <D-x> "*d
vmap <D-c> "*y
vmap <D-v> "-d"*P
nmap <D-v> "*P
let &cpo=s:cpo_save
unlet s:cpo_save
set autoindent
set background=dark
set backspace=2
set clipboard=unnamed
set cscopeprg=/usr/bin/cscope
set cscopetag
set cscopeverbose
set errorformat=%A%*[^[][javac]\ %f:%l:\ %m,%Z%*[^[][javac]\ %p^,%C%*[^[][javac]\ %m,%-G%*[^[][javac]\ %.%#,%-G%.%#
set expandtab
set fileencodings=ucs-bom,utf-8,default,latin1
set grepprg=grep\ -nH\ $*
set helplang=en
set history=50
set hlsearch
set nojoinspaces
set makeprg=ant\ -s\ build.xml\ jar
set modelines=0
set path=.,,.;
set printfont=Courier:h10
set printoptions=paper:letter,header:0
set ruler
set runtimepath=~/.vim,/usr/share/vim/vimfiles,/usr/share/vim/vim91,/usr/share/vim/vim91/pack/dist/opt/netrw,/usr/share/vim/vimfiles/after,~/.vim/after,~/go/misc/vim
set shiftwidth=2
set softtabstop=2
set splitright
set suffixes=.bak,~,.o,.h,.info,.swp,.obj,.class
set tabstop=2
set tags=./tags,./TAGS,tags,TAGS,./../tags,./../tags,./../../tags,./../../../tags
set textwidth=100
set title
set titlelen=70
set titlestring=%<%F
set viminfo='20,\"50
set wildmode=longest:full
set window=0
set wrapmargin=4
" vim: set ft=vim :
