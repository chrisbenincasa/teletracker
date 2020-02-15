import React from 'react';
import Toolbar from '../components/Toolbar/Toolbar';
import { useState } from 'react';
import { makeStyles, Theme } from '@material-ui/core';
import Drawer from '../components/Drawer';
import Footer from '../components/Footer';
import { Store } from 'redux';
import { connect } from 'react-redux';
import { AppState } from '../reducers';
import { useRouter } from 'next/router';

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
  store: Store;
  isAuthed: boolean;
  children: any;
}

function AppWrapper(props: Props) {
  let [drawerOpen, setDrawerOpen] = useState(false);
  let router = useRouter();
  let classes = useStyles();

  return (
    <div className={classes.root}>
      <Toolbar
        drawerOpen={drawerOpen}
        onDrawerChange={() => setDrawerOpen(!drawerOpen)}
      />
      <div style={{ flexGrow: 1 }}>
        <Drawer open={drawerOpen} closeRequested={() => setDrawerOpen(false)} />
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
      {router.pathname.toLowerCase() === '/popular' ||
      (props.isAuthed && router.pathname === '/') ? null : (
        <Footer />
      )}
    </div>
  );
}

export default connect((state: AppState) => {
  return {
    isAuthed: !!state.auth.token,
  };
})(AppWrapper);
