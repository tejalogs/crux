package dev.dimension.flare.ui.screen.home

/** Adds [css] and hides posts and prompts by their label. Only sets display:none; blocks nothing. */
private fun hideOnlyScript(
    css: String,
    posts: String,
    labels: String,
    prompts: String,
): String =
    """
    (function(){
      var s=document.createElement('style');s.textContent=${'"'}$css${'"'};
      (document.head||document.documentElement).appendChild(s);
      var hide=function(e){e.style.setProperty('display','none','important')};
      var labels=$labels,prompts=$prompts;
      var bar=function(e){
        while(e.parentElement&&e.parentElement!==document.body&&e.parentElement.getBoundingClientRect().height<=120){e=e.parentElement}
        return e;
      };
      var scan=function(){
        document.querySelectorAll('$posts').forEach(function(p){
          if(p.style.display==='none')return;
          var t=p.querySelectorAll('span');
          for(var i=0;i<t.length;i++){if(labels.test(t[i].textContent.trim())){hide(p);break}}
        });
        document.querySelectorAll('button,a,[role=button]').forEach(function(b){
          if(!prompts.test((b.textContent||'').trim()))return;
          var d=b.closest('[role=dialog]');
          if(d){hide(d);document.body.style.overflow=''}else{hide(bar(b))}
        });
      };
      var timer=0;
      // Runs only when the page adds content, and at most once every 500 ms.
      new MutationObserver(function(){if(!timer)timer=setTimeout(function(){timer=0;scan()},500)})
        .observe(document,{childList:true,subtree:true});
    })();
    """.trimIndent()

internal val INSTAGRAM_PAGE_SCRIPT =
    hideOnlyScript(
        css = "article:has(a[href*='/ads/ig_redirect']){display:none!important}",
        posts = "article",
        labels = "/^(Sponsored|Suggested for you|Suggested posts|Suggested for you\\.)$/i",
        prompts = "/^(Use the app|Open app|Open in app|Get the app|Open Instagram)$/i",
    )

// LinkedIn's phone layout grows past the screen because its bottom bar sets the grid width.
internal val LINKEDIN_PAGE_SCRIPT =
    hideOnlyScript(
        css =
            "div:has(>footer){grid-template-columns:minmax(0,1fr)!important}" +
                "footer,main{min-width:0!important}footer li{min-width:0!important;flex:1 1 0!important}",
        posts = "[role=listitem]",
        labels = "/^(Promoted|Sponsored)$/i",
        prompts = "/^(Use app|Use the app|Open app|Open in app|Get the app|Open the app)$/i",
    )
