import React, { useEffect, useCallback, useState } from 'react';
import {
  createStyles,
  IconButton,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { KeyboardArrowUp } from '@material-ui/icons';
import classNames from 'classnames';
import ScrollToTop from './Buttons/ScrollToTop';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    scrollToTop: {
      position: 'fixed',
      bottom: theme.spacing(1),
      right: theme.spacing(3),
      zIndex: theme.zIndex.appBar,
    },
  }),
);

interface Props {
  children: React.ReactNode;
}

export default function ScrollToTopButton(props: Props) {
  const classes = useStyles();
  let [showScrollToTop, setShowScrollToTop] = useState(false);

  const onScroll = useCallback(() => {
    const scrollTop = window.pageYOffset || 0;
    // to do: 100 is just a random number, we can play with this or make it dynamic
    if (scrollTop > 200 && !showScrollToTop) {
      setShowScrollToTop(true);
      console.log('on');
    } else {
      setShowScrollToTop(false);
    }
  }, []);

  useEffect(() => {
    window.addEventListener('scroll', onScroll, false);

    return () => {
      window.removeEventListener('scroll', onScroll);
    };
  }, []);

  const scrollToTop = useCallback(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  return (
    <React.Fragment>
      {props.children}
      <ScrollToTop
        show={showScrollToTop}
        onClick={scrollToTop}
        className={classes.scrollToTop}
      />
    </React.Fragment>
  );
}
