(java-import "javax.swing.tree.TreeModel")
(require-lib "rainbow/swing")
(require-lib "rainbow/welder")

(= tagged-writers!tree-node (fn (node)
  (let node-name (node!name)
    (if (find node-name (arc-path))
        node-name
        (last:tokens node-name #\/)))))

(set pb-cache (table))

(def make-tree-node (root isleaf kidfn)
  (or= pb-cache.root
    (let children nil
      (annotate 'tree-node (make-obj
        (invalidate  ()      (wipe children))
        (name        ()      root)
        (kids        ()      (or= children (kidfn)))
        (nth         (index) ((kids) index))
        (count       ()      (aif (kids) (len it) 0))
        (leaf        ()      isleaf)
        (child-index (child)
          (index-of child (map rep (kids)))))))))

(def make-node (root)
  (make-tree-node root
                  (no:dir-exists root)
                  (if dir-exists.root
                      (fn () (map [make-node (+ root "/" _)]
                                  (dir root)))
                      nilfn)))

(def make-root-node ()
  (make-tree-node "(arc-path)"
                  nil
                  (fn () (map make-node (arc-path)))))

(def pb-tree-model ()
  (withs (root-node (make-root-node)
          listeners nil)
    (TreeModel implement t (make-obj
      (equals                  (other)      nil)
      (getRoot                 ()           root-node)
      (getChild                (node index) (rep.node!nth index))
      (getChildCount           (node)       (rep.node!count))
      (isLeaf                  (node)       (rep.node!leaf))
      (valueForPathChanged     (tree-path node)
        (prn 'valueForPathChanged " " tree-path " node: " rep.node) )
      (getIndexOfChild         (parent child)
        (prn "looking for index of " (rep.child!name) " under " (rep.parent!name))
        (rep.parent!child-index rep.child))
      (addTreeModelListener    (listener)   (push listener listeners))
      (removeTreeModelListener (listener)   (zap [rem listener _] listeners))))))

(def refresh-path-browser (tree)
  (with (expanded-paths (tree 'getExpandedDescendants (tree 'getPathForRow 0))
         selection-paths tree!getSelectionPaths
         model (pb-tree-model))
    (tree 'setModel model)
    (ontable path node pb-cache (rep.node!invalidate))
    (while expanded-paths!hasMoreElements
      (tree 'expandPath expanded-paths!nextElement))
    (tree 'addSelectionPaths selection-paths)))

(def path-browser ()
  (withs (f (frame 150 150 800 800 "arc-path browser")
          tree (java-new "javax.swing.JTree")
          renderer (bean "javax.swing.JLabel")
          model    (pb-tree-model)
          sc (scroll-pane tree colour-scheme!background))
    (tree 'setModel model)
    (on-key-press tree
      'enter (welder (((rep tree!getSelectionPath!getLastPathComponent) 'name)))
      'f5    (refresh-path-browser tree))
    (f 'add sc)
    f!pack
    f!show))
