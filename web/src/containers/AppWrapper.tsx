import React, { useState } from 'react';
import Toolbar from '../components/Toolbar/Toolbar';
import { LinearProgress, makeStyles, NoSsr, Theme } from '@material-ui/core';
import Drawer from '../components/Drawer';
import Footer from '../components/Footer';
import { useRouter } from 'next/router';
import { WithUser } from '../hooks/useWithUser';
import useStateSelector from '../hooks/useStateSelector';
import _ from 'lodash';

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

export default function AppWrapper(props: Props) {
  const isBooting = useStateSelector(state => state.startup.isBooting);
  const isAuthed = useStateSelector(state => !_.isUndefined(state.auth.token));
  const [drawerOpen, setDrawerOpen] = useState(false);
  const router = useRouter();
  const classes = useStyles();

  return (
    <div className={classes.root}>
      <WithUser>
        <NoSsr>
          <Toolbar
            drawerOpen={drawerOpen}
            onDrawerChange={shouldClose =>
              setDrawerOpen(
                !_.isUndefined(shouldClose) ? !shouldClose : !drawerOpen,
              )
            }
            showToolbarSearch={true}
          />
        </NoSsr>
        {!isBooting ? (
          <div style={{ flexGrow: 1 }}>
            <Drawer
              open={drawerOpen}
              closeRequested={() => setDrawerOpen(false)}
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
        {router.pathname.toLowerCase() === '/popular' ||
        (isAuthed && router.pathname === '/') ? null : (
          <Footer />
        )}
      </WithUser>
    </div>
  );
}
