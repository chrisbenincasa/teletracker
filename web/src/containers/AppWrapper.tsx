import React, { useCallback, useEffect, useState } from 'react';
import Toolbar from '../components/Toolbar/Toolbar';
import { LinearProgress, makeStyles, NoSsr, Theme } from '@material-ui/core';
import Drawer from '../components/Drawer';
import { WithUser } from '../hooks/useWithUser';

import useStateSelector from '../hooks/useStateSelector';
import _ from 'lodash';
import { initGA, logPageView } from '../utils/analytics';
import { useRouter } from 'next/router';
import qs from 'querystring';
import url from 'url';
import WithAppContext from '../components/AppContext';

const useStyles = makeStyles((theme: Theme) => ({
  mainContent: {
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
    paddingBottom: '10rem',
    [theme.breakpoints.down('sm')]: {
      paddingBottom: '2rem',
    },
  },
  root: {
    flexGrow: 1,
    minHeight: '100vh',
    position: 'relative',
    display: 'flex',
    flexDirection: 'column',
  },
}));

interface Props {
  children: any;
}

declare global {
  interface Window {
    GA_INITIALIZED: boolean;
  }
}

export default function AppWrapper(props: Props) {
  const isBooting = useStateSelector(state => state.startup.isBooting);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const classes = useStyles();
  const nextRouter = useRouter();
  const query = nextRouter.query.q as string | undefined;

  const [showToolbarSearch, setShowToolbarSearch] = useState(
    _.isUndefined(query),
  );

  const inViewportCallback = useCallback((inViewport: boolean) => {
    setShowToolbarSearch(!inViewport);
  }, []);

  useEffect(() => {
    if (window && !window.GA_INITIALIZED) {
      initGA();
      window.GA_INITIALIZED = true;
    }

    logPageView(nextRouter.asPath);
  }, []);

  return (
    <NoSsr>
      <div className={classes.root}>
        <WithAppContext inViewportCallback={inViewportCallback}>
          <WithUser>
            <Toolbar
              drawerOpen={drawerOpen}
              onDrawerChange={shouldClose =>
                setDrawerOpen(
                  !_.isUndefined(shouldClose) ? !shouldClose : !drawerOpen,
                )
              }
              showToolbarSearch={
                _.isUndefined(showToolbarSearch) ? true : showToolbarSearch
              }
            />
            {!isBooting ? (
              <div style={{ flexGrow: 1 }}>
                <Drawer
                  open={drawerOpen}
                  closeRequested={() => setDrawerOpen(false)}
                  drawerStateChanged={value => setDrawerOpen(value)}
                />
                <main
                  style={{
                    display: 'flex',
                    flexDirection: 'column', // isAuthed ? 'row' : 'column',
                  }}
                  className={classes.mainContent}
                >
                  {props.children}
                </main>
              </div>
            ) : (
              <LinearProgress />
            )}
          </WithUser>
        </WithAppContext>
      </div>
    </NoSsr>
  );
}
