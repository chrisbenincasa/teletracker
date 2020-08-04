import React, { useState } from 'react';
import './App.css';
import {
  AppBar,
  createStyles,
  Divider,
  Drawer,
  Hidden,
  IconButton,
  List,
  ListItem,
  ListItemText,
  makeStyles,
  Theme,
  Toolbar,
  useTheme,
  ListItemIcon,
  CssBaseline,
  Typography,
} from '@material-ui/core';
import { LiveTv, Menu, Storage } from '@material-ui/icons';
import { Link, Redirect, Router } from '@reach/router';
import Matching from './features/matching/Matching';
import MatchInspector from './features/match_inspector/MatchInspector';
import Tasks from './features/tasks/Tasks';

const drawerWidth = 240;

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    root: {
      display: 'flex',
    },
    drawer: {
      [theme.breakpoints.up('sm')]: {
        width: drawerWidth,
        flexShrink: 0,
      },
      zIndex: theme.zIndex.drawer,
    },
    drawerItem: {
      textDecoration: 'none',
    },
    appBar: {
      zIndex: theme.zIndex.appBar + 200,
    },
    menuButton: {
      marginRight: theme.spacing(2),
      [theme.breakpoints.up('sm')]: {
        display: 'none',
      },
    },
    // necessary for content to be below app bar
    toolbar: theme.mixins.toolbar,
    drawerPaper: {
      width: drawerWidth,
    },
    drawerHeader: {
      display: 'flex',
      alignItems: 'center',
      padding: theme.spacing(0, 1),
      // necessary for content to be below app bar
      ...theme.mixins.toolbar,
      justifyContent: 'flex-end',
    },
    content: {
      flexGrow: 1,
      padding: theme.spacing(3),
    },
  }),
);

function App() {
  const classes = useStyles();
  const theme = useTheme();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const drawer = (
    <div>
      <div className={classes.toolbar} />
      <Divider />
      <List>
        <Link to="/matching/unmatched/all" style={{ textDecoration: 'none' }}>
          <ListItem button>
            <ListItemIcon>
              <LiveTv />
            </ListItemIcon>
            <ListItemText primary="Matching" className={classes.drawerItem} />
          </ListItem>
        </Link>
        <Link to="/tasks/">
          <ListItem button>
            <ListItemIcon>
              <Storage />
            </ListItemIcon>
            <ListItemText primary="Tasks" className={classes.drawerItem} />
          </ListItem>
        </Link>
      </List>
    </div>
  );

  const container =
    window !== undefined ? () => window.document.body : undefined;

  return (
    <div className={classes.root}>
      <CssBaseline />
      <AppBar className={classes.appBar}>
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            className={classes.menuButton}
          >
            <Menu />
          </IconButton>
          <Typography
            variant="h6"
            color="inherit"
            component="a"
            style={{ textDecoration: 'none' }}
          >
            Teletracker <span style={{ color: 'red' }}>Admin</span>
          </Typography>
        </Toolbar>
      </AppBar>
      <nav className={classes.drawer} aria-label="mailbox folders">
        {/* The implementation can be swapped with js to avoid SEO duplication of links. */}
        <Hidden smUp implementation="css">
          <Drawer
            container={container}
            variant="temporary"
            anchor={theme.direction === 'rtl' ? 'right' : 'left'}
            open={mobileOpen}
            onClose={handleDrawerToggle}
            classes={{
              paper: classes.drawerPaper,
            }}
            ModalProps={{
              keepMounted: true, // Better open performance on mobile.
            }}
          >
            {drawer}
          </Drawer>
        </Hidden>
        <Hidden xsDown implementation="css">
          <Drawer
            classes={{
              paper: classes.drawerPaper,
            }}
            variant="permanent"
            open
          >
            {drawer}
          </Drawer>
        </Hidden>
      </nav>
      <main className={classes.content}>
        <div className={classes.toolbar} />
        <Router>
          <Redirect noThrow from="/matching" to={'/matching/unmatched/all'} />
          <Matching path="/matching/:filterType/:networkScraper" />
          <MatchInspector path="/match_inspector/" />
          <Tasks path="/tasks/" />
        </Router>
      </main>
    </div>
  );
}

export default App;
