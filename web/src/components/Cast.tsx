import React, {
  Component,
  RefObject,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  Avatar,
  createStyles,
  IconButton,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import {
  ChevronLeft,
  ChevronRight,
  Person,
  TheatersSharp,
} from '@material-ui/icons';
import RouterLink from 'next/link';
import { FixedSizeList as LazyList } from 'react-window';
import AutoSizer from 'react-virtualized-auto-sizer';
import { Item, ItemCastMember } from '../types/v2/Item';
import { useStateDeepEq } from '../hooks/useStateDeepEq';
import { setServers } from 'dns';
import { makeStyles } from '@material-ui/core/styles';
import { usePrevious } from '../hooks/usePrevious';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    avatar: {
      width: 100,
      height: 100,
      fontSize: '3rem',
      boxShadow: theme.shadows[1],
      '&:hover': {
        opacity: 0.8,
        transition: theme.transitions.create(['backgroundColor', 'opacity'], {
          duration: theme.transitions.duration.leavingScreen,
          easing: theme.transitions.easing.easeIn,
        }),
      },
    },
    avatarLink: {
      textDecoration: 'none',
      display: 'block',
      height: '100%',
      cursor: 'pointer',
    },
    actualName: {
      fontWeight: theme.typography.fontWeightBold,
      fontSize: '0.9rem',
    },
    castNavigation: {
      display: 'flex',
      flexDirection: 'row',
      justifyContent: 'flex-end',
      flexGrow: 1,
    },
    castWrapper: {
      display: 'flex',
      flexDirection: 'row',
      justifyContent: 'flex-start',
    },
    characterName: {
      fontStyle: 'italic',
      fontSize: '0.7rem',
    },
    grid: {
      [theme.breakpoints.up('sm')]: {
        justifyContent: 'flex-start',
      },
      justifyContent: 'space-around',
      height: 180,
    },
    header: {
      padding: theme.spacing(1, 0),
      fontWeight: 700,
    },
    personContainer: {
      display: 'flex',
      flexDirection: 'column',
      maxWidth: 100,
      margin: theme.spacing(1),
    },
  }),
);

interface OwnProps {
  itemDetail: Item;
}

type Props = OwnProps;

export default function Cast(props: Props) {
  const [currentCarouselIndex, setCurrentCarouselIndex] = useState(0);
  const [totalCast, setTotalCast] = useState(0);
  const [numberCastRemaining, setNumberCastRemaining] = useState(0);
  const [castPerPage, setCastPerPage] = useState(0);
  const listRef = useRef<LazyList>(null);
  const prevListRef = usePrevious(listRef);
  const classes = useStyles();

  useEffect(() => {
    if (listRef.current) {
      const credits = props.itemDetail.cast || [];
      const width = listRef.current?.props?.width;
      const itemSize = listRef.current?.props?.itemSize;
      const pageSize = Math.round(Number(width) / Number(itemSize));

      setTotalCast(credits.length);
      setCastPerPage(pageSize);
      setNumberCastRemaining(
        credits.length - pageSize < 0 ? 0 : credits.length - pageSize,
      );
    }
  }, [listRef.current, props.itemDetail]);

  const carouselNavigationPrevious = () => {
    const remainingCast =
      numberCastRemaining > 0 ? numberCastRemaining + castPerPage : castPerPage;
    const newIndex = currentCarouselIndex - castPerPage;

    setCurrentCarouselIndex(newIndex < 0 ? 0 : newIndex);
    setNumberCastRemaining(remainingCast < 1 ? 0 : remainingCast);
    listRef.current?.scrollToItem(newIndex, 'start');
  };

  const carouselNavigationNext = () => {
    const remainingCast =
      (numberCastRemaining > 0
        ? numberCastRemaining
        : totalCast - castPerPage) - castPerPage;
    const newIndex =
      numberCastRemaining > castPerPage
        ? currentCarouselIndex + castPerPage
        : currentCarouselIndex + numberCastRemaining;

    setCurrentCarouselIndex(newIndex > totalCast ? totalCast : newIndex);
    setNumberCastRemaining(remainingCast < 0 ? 0 : remainingCast);

    listRef.current?.scrollToItem(newIndex, 'start');
  };

  const credits = props.itemDetail?.cast || [];
  const previousDisabled = currentCarouselIndex === 0;
  const nextDisabled = numberCastRemaining < 1;

  const renderAvatar = (castMember: ItemCastMember) => {
    const WrappedAvatar = React.forwardRef(({ onClick, href }: any, ref) => {
      return (
        <a
          href={href}
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          className={classes.avatarLink}
        >
          <Avatar
            alt={`Photo of ${castMember.name}`}
            src={
              castMember.person && castMember.person.profile_path
                ? `https://image.tmdb.org/t/p/w185/${castMember.person.profile_path}`
                : ''
            }
            className={classes.avatar}
            itemProp="image"
          >
            {castMember.person && castMember.person.profile_path ? null : (
              <Person style={{ fontSize: '5rem' }} />
            )}
          </Avatar>
          <Typography
            color="textPrimary"
            className={classes.actualName}
            align="center"
            itemProp="name"
          >
            {castMember.name}
          </Typography>
          <Typography
            color="textPrimary"
            className={classes.characterName}
            align="center"
            itemProp="character"
          >
            {castMember.character}
          </Typography>
        </a>
      );
    });

    return (
      <RouterLink
        href={'/person/[id]?id=' + castMember.slug}
        as={'/person/' + castMember.slug}
      >
        <WrappedAvatar />
      </RouterLink>
    );
  };

  const CastMember = ({ index, style }) => (
    <div
      className={classes.personContainer}
      key={credits[index].id}
      itemProp="actor"
      itemScope
      itemType="http://schema.org/Person"
      style={style}
    >
      {renderAvatar(credits[index])}
    </div>
  );

  return (
    <React.Fragment>
      <div className={classes.castWrapper}>
        <Typography color="inherit" variant="h5" className={classes.header}>
          Cast
        </Typography>
        {previousDisabled && nextDisabled ? null : (
          <div className={classes.castNavigation}>
            <IconButton
              onClick={carouselNavigationPrevious}
              size="medium"
              style={{
                cursor: previousDisabled ? undefined : 'pointer',
                padding: 6,
                touchAction: 'manipulation',
              }}
              color={previousDisabled ? 'secondary' : undefined}
              disabled={previousDisabled}
            >
              <ChevronLeft
                style={{
                  fontSize: '2.5rem',
                }}
              />
            </IconButton>
            <IconButton
              style={{
                cursor: nextDisabled ? undefined : 'pointer',
                padding: 6,
                touchAction: 'manipulation',
              }}
              size="medium"
              color={nextDisabled ? 'secondary' : undefined}
              onClick={carouselNavigationNext}
              disabled={nextDisabled}
            >
              <ChevronRight
                style={{
                  fontSize: '2.5rem',
                }}
              />
            </IconButton>
          </div>
        )}
      </div>
      <div className={classes.grid}>
        <AutoSizer>
          {({ height, width }) => (
            <LazyList
              height={190}
              itemCount={credits.length}
              itemSize={125}
              layout="horizontal"
              width={width}
              overscanCount={castPerPage}
              style={{ overflow: 'hidden' }}
              ref={listRef}
            >
              {CastMember}
            </LazyList>
          )}
        </AutoSizer>
      </div>
    </React.Fragment>
  );
}
